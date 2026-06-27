package com.ecommerce.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class JCClawService {

    private static final String ILINK_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String ILINK_APP_ID = "bot";
    private static final String ILINK_APP_CLIENT_VERSION = "132100";
    private static final String BOT_TYPE = "3";

    private static final Path STATE_DIR = Path.of(System.getProperty("user.home"), ".openclaw");
    private static final Path ACCOUNTS_DIR = STATE_DIR.resolve("openclaw-weixin").resolve("accounts");
    private static final Path ACCOUNTS_INDEX = STATE_DIR.resolve("openclaw-weixin").resolve("accounts.json");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final WeChatBotService weChatBot;

    private String currentQrcode;
    private String botToken;
    private String accountId;
    private String userId;

    public JCClawService(WeChatBotService weChatBot) { this.weChatBot = weChatBot; }

    /** 服务启动时或需要时从磁盘加载凭证并恢复 Bot 连接 */
    @jakarta.annotation.PostConstruct
    public void restoreFromDisk() {
        try {
            if (!Files.exists(ACCOUNTS_INDEX)) {
                log.info("未找到已保存的微信凭证，跳过自动恢复");
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> ids = objectMapper.readValue(Files.readString(ACCOUNTS_INDEX), List.class);
            if (ids == null || ids.isEmpty()) {
                log.info("凭证索引为空，跳过自动恢复");
                return;
            }

            String id = ids.get(0);
            Path acctFile = ACCOUNTS_DIR.resolve(id + ".json");
            if (!Files.exists(acctFile)) {
                log.warn("凭证文件不存在: {}", acctFile);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> acct = objectMapper.readValue(Files.readString(acctFile), Map.class);
            String token = (String) acct.get("token");
            if (token == null || token.isBlank()) {
                log.warn("凭证文件中无 token: {}", acctFile);
                return;
            }

            this.botToken = token;
            this.accountId = id;
            this.userId = (String) acct.get("userId");

            weChatBot.start(botToken, accountId, userId);
            log.info("已从磁盘恢复微信 Bot 连接: accountId={}", accountId);
        } catch (Exception e) {
            log.warn("自动恢复微信 Bot 连接失败: {}", e.getMessage());
        }
    }

    /** 发起扫码绑定（清除旧凭证后获取新二维码） */
    public Map<String, Object> startLogin(String openclawHome) throws Exception {
        return startLogin(openclawHome, false);
    }

    /** 发起扫码绑定。默认不抢占已有连接，避免多用户扫码互相踢下线。 */
    public synchronized Map<String, Object> startLogin(String openclawHome, boolean forceTakeover) throws Exception {
        if (!forceTakeover && (weChatBot.isRunning() || botToken != null || accountId != null)) {
            Map<String, Object> conflict = getConnectionStatus();
            conflict.put("success", false);
            conflict.put("conflict", true);
            conflict.put("message", "当前已有活动微信连接。如需重新扫码，请先确认接管连接。");
            return conflict;
        }

        clearCredentials();
        weChatBot.stop();
        botToken = null;
        accountId = null;
        userId = null;

        Map<String, Object> result = new LinkedHashMap<>();
        String url = ILINK_BASE_URL + "/ilink/bot/get_bot_qrcode?bot_type=" + BOT_TYPE;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("iLink-App-Id", ILINK_APP_ID)
                .header("iLink-App-ClientVersion", ILINK_APP_CLIENT_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString("{\"local_token_list\":[]}")).build();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body(), Map.class);
        String qr = (String) data.get("qrcode_img_content");
        if (qr == null || qr.isBlank()) { result.put("success", false); result.put("error", "iLink异常"); return result; }
        this.currentQrcode = (String) data.get("qrcode");
        result.put("success", true); result.put("qrUrl", qr); result.put("status", "binding");
        return result;
    }

    public synchronized Map<String, Object> checkStatus(String openclawHome) {
        Map<String, Object> result = getConnectionStatus();
        result.put("connected", weChatBot.isRunning());
        if (currentQrcode == null) return result;

        try {
            String url = ILINK_BASE_URL + "/ilink/bot/get_qrcode_status?qrcode=" + URLEncoder.encode(currentQrcode, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("iLink-App-Id", ILINK_APP_ID).header("iLink-App-ClientVersion", ILINK_APP_CLIENT_VERSION).GET().build();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body(), Map.class);
            String s = (String) data.get("status");
            log.info("iLink状态: {}", s);

            switch (s) {
                case "confirmed" -> {
                    botToken = (String) data.get("bot_token");
                    accountId = (String) data.get("ilink_bot_id");
                    userId = (String) data.get("ilink_user_id");
                    currentQrcode = null;
                    saveCredentials();
                    weChatBot.start(botToken, accountId, userId);
                    result.put("connected", true);
                }
                case "binded_redirect" -> {
                    // iLink 服务器已有绑定会话，尝试从磁盘加载凭证自动恢复
                    if (!weChatBot.isRunning()) {
                        log.info("检测到已有绑定会话，尝试从磁盘恢复...");
                        restoreFromDisk();
                    }
                    result.put("connected", weChatBot.isRunning());
                    result.put("alreadyConnected", true);
                    currentQrcode = null;
                }
                case "expired" -> { result.put("expired", true); currentQrcode = null; }
            }
        } catch (Exception e) { log.debug("轮询: {}", e.getMessage()); }
        return result;
    }

    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean running = weChatBot.isRunning();
        status.put("running", running);
        status.put("connected", running);
        status.put("binding", currentQrcode != null);
        status.put("accountId", accountId != null ? accountId : "");
        status.put("userId", userId != null ? userId : "");
        status.put("savedAccountCount", countSavedAccounts());
        status.put("mode", "single_active_connection");
        status.put("multiUserPolicy", "已有连接时默认阻止新扫码抢占；需要显式接管。");
        status.put("conflictRisk", running || currentQrcode != null);
        return status;
    }

    public synchronized void disconnect(boolean clearSavedCredentials) {
        weChatBot.stop();
        currentQrcode = null;
        botToken = null;
        accountId = null;
        userId = null;
        if (clearSavedCredentials) {
            clearCredentials();
        }
    }

    private void saveCredentials() {
        if (botToken == null || accountId == null) return;
        try {
            Path weixinDir = STATE_DIR.resolve("openclaw-weixin");
            Path accountsDir = weixinDir.resolve("accounts");
            Files.createDirectories(accountsDir);

            Map<String, Object> acct = new LinkedHashMap<>();
            acct.put("token", botToken);
            acct.put("savedAt", java.time.Instant.now().toString());
            if (userId != null) acct.put("userId", userId);
            Files.writeString(accountsDir.resolve(accountId + ".json"), objectMapper.writeValueAsString(acct));

            Path idx = weixinDir.resolve("accounts.json");
            List<String> ids = new ArrayList<>();
            if (Files.exists(idx)) try { ids = new ArrayList<>(objectMapper.readValue(Files.readString(idx), List.class)); } catch (Exception ignored) {}
            if (!ids.contains(accountId)) { ids.add(accountId); Files.writeString(idx, objectMapper.writeValueAsString(ids)); }
            log.info("凭证已保存: {}", accountId);
        } catch (Exception e) { log.error("保存凭证失败", e); }
    }

    /** 清除旧凭证 */
    private void clearCredentials() {
        try {
            if (Files.exists(ACCOUNTS_INDEX)) Files.delete(ACCOUNTS_INDEX);
            if (Files.exists(ACCOUNTS_DIR)) {
                try (var s = Files.list(ACCOUNTS_DIR)) { s.forEach(f -> { try { Files.delete(f); } catch (Exception ignored) {} }); }
            }
        } catch (Exception e) { log.debug("清除凭证: {}", e.getMessage()); }
    }

    public void cancelLogin() { currentQrcode = null; }

    private int countSavedAccounts() {
        try {
            if (!Files.exists(ACCOUNTS_INDEX)) return 0;
            @SuppressWarnings("unchecked")
            List<String> ids = objectMapper.readValue(Files.readString(ACCOUNTS_INDEX), List.class);
            return ids == null ? 0 : ids.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
