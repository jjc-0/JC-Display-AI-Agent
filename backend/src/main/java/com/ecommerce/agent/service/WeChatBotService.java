package com.ecommerce.agent.service;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微信 Bot 消息服务 — v2
 *
 * 接入 AgentRuntime，微信用户消息 → Agent 自主决策 → 工具调用 → 回复。
 * 同时暴露 sendMessage/getActiveUsers 供 WeChatTool 调用。
 */
@Slf4j
@Service
public class WeChatBotService {

    private static final String ILINK_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String ILINK_APP_ID = "bot";
    private static final String ILINK_APP_CLIENT_VERSION = "132100";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentRuntime agentRuntime;
    private final ConversationManager conversationManager;

    private Thread pollThread;
    private volatile boolean running = false;
    private String botToken;
    private String accountId;
    private String ilinkUserId;
    private String typingTicket;
    private String getUpdatesBuf = "";

    /** fromUserId → sessionId */
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    /** fromUserId → last activity timestamp */
    private final Map<String, Long> userLastActive = new ConcurrentHashMap<>();

    public WeChatBotService(AgentRuntime agentRuntime, ConversationManager conversationManager) {
        this.agentRuntime = agentRuntime;
        this.conversationManager = conversationManager;
    }

    // ═══════════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════════

    public synchronized void start(String token, String accountId, String ilinkUserId) {
        if (running) return;
        this.botToken = token;
        this.accountId = accountId;
        this.ilinkUserId = ilinkUserId;
        this.typingTicket = null;
        this.getUpdatesBuf = "";
        this.running = true;
        pollThread = new Thread(this::pollLoop, "wechat-poll");
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("微信Bot消息轮询已启动(v2 AgentRuntime)");
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
    }

    public boolean isRunning() { return running; }

    // ═══════════════════════════════════════════════════════════════
    // 公共 API — 供 WeChatTool 调用
    // ═══════════════════════════════════════════════════════════════

    /** 主动给微信用户发消息 (由 Agent 工具调用) */
    public boolean sendMessage(String toUserId, String text) {
        if (!running) return false;
        try {
            // 查找该用户的 contextToken (从最近消息中获取)
            String sessionId = userSessions.get(toUserId);
            String contextToken = null;
            if (sessionId != null) {
                var history = conversationManager.getDBHistory(sessionId);
                if (history != null && !history.isEmpty()) {
                    contextToken = history.get(history.size() - 1).getContent();
                }
            }

            sendIlinkMessage(toUserId, text, contextToken);
            return true;
        } catch (Exception e) {
            log.error("微信发送失败 to={}: {}", toUserId, e.getMessage());
            return false;
        }
    }

    /** 获取最近活跃的微信用户列表 */
    public List<Map<String, Object>> getActiveUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (var entry : userLastActive.entrySet()) {
            String uid = entry.getKey();
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("user_id", uid);
            u.put("last_active", entry.getValue());
            u.put("minutes_ago", (now - entry.getValue()) / 60000);

            String sid = userSessions.get(uid);
            if (sid != null) {
                u.put("session_id", sid);
                u.put("message_count", conversationManager.getHistory(uid).size());
            }
            users.add(u);
        }

        users.sort((a, b) -> Long.compare(
                (Long) b.getOrDefault("last_active", 0L),
                (Long) a.getOrDefault("last_active", 0L)));
        return users;
    }

    // ═══════════════════════════════════════════════════════════════
    // 消息轮询
    // ═══════════════════════════════════════════════════════════════

    private void pollLoop() {
        while (running) {
            try {
                List<Map<String, Object>> messages = fetchUpdates();
                if (!messages.isEmpty()) log.info("[微信] 收到 {} 条", messages.size());
                for (Map<String, Object> msg : messages) processMessage(msg);
            } catch (InterruptedException e) { break;
            } catch (Exception e) { log.warn("轮询异常: {}", e.getMessage()); try { Thread.sleep(3000); } catch (InterruptedException ie) { break; } }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchUpdates() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("get_updates_buf", getUpdatesBuf);
        body.put("base_info", baseInfo());

        HttpRequest req = request("ilink/bot/getupdates", body, 40);
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("[getupdates] HTTP {} : {}", resp.statusCode(),
                    resp.body().substring(0, Math.min(200, resp.body().length())));
        }
        Map<String, Object> data = objectMapper.readValue(resp.body(), Map.class);

        Object ret = data.get("ret");
        if (ret instanceof Number n && n.intValue() != 0) log.warn("[getupdates] ret={}", ret);

        Object errcode = data.get("errcode");
        if (errcode instanceof Number n && n.intValue() != 0) {
            log.warn("[getupdates] errcode={} msg={}", errcode, data.get("errmsg"));
            if (n.intValue() == -14) {
                log.warn("微信 Bot 会话已过期，停止轮询");
                running = false;
            }
        }

        Object buf = data.get("next_get_updates_buf");
        if (buf instanceof String s && !s.isBlank()) getUpdatesBuf = s;

        Object msgs = data.get("msgs");
        if (msgs instanceof List) return (List<Map<String, Object>>) msgs;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private void processMessage(Map<String, Object> msg) {
        try {
            String fromUserId = toString(msg.get("from_user_id"));
            if (fromUserId.isBlank()) return;

            String text = "";
            Object items = msg.get("item_list");
            if (items instanceof List list) {
                for (Object item : list) {
                    if (item instanceof Map m) {
                        Object type = m.get("type");
                        if (type instanceof Number n && n.intValue() == 1) {
                            Object ti = m.get("text_item");
                            if (ti instanceof Map tm) text += toString(tm.get("text"));
                        }
                    }
                }
            }
            if (text.isBlank()) return;

            log.info("[微信] {}: {}", fromUserId, text);

            String contextToken = toString(msg.get("context_token"));
            userLastActive.put(fromUserId, System.currentTimeMillis());

            // 获取 typing_ticket
            if (typingTicket == null && ilinkUserId != null && !ilinkUserId.isBlank()
                    && contextToken != null && !contextToken.isBlank()) {
                fetchTypingTicket(contextToken);
            }

            // 创建/获取 session
            String sessionId = userSessions.computeIfAbsent(fromUserId, k -> {
                String sid = "wx_" + k;
                return conversationManager.createSession(sid, "微信对话", "wechat");
            });

            // ═══ v2: 使用 AgentRuntime 替代 AgentDispatcher ═══
            AgentRequest req = AgentRequest.builder()
                    .sessionId(sessionId)
                    .message(text)
                    .taskType("wechat")
                    .enableTools(true)
                    .build();

            sendTyping(true);
            try {
                AgentResponse agentResp = agentRuntime.execute(req);
                String reply = agentResp.getMessage();
                if (reply == null || reply.isBlank()) reply = "智能体暂无响应";

                log.info("[微信回复] {}", reply.substring(0, Math.min(80, reply.length())));
                sendIlinkMessage(fromUserId, reply, contextToken);
            } finally {
                sendTyping(false);
            }

        } catch (Exception e) {
            log.error("处理微信消息失败", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // iLink API 通信
    // ═══════════════════════════════════════════════════════════════

    private void sendIlinkMessage(String toUserId, String text, String contextToken) throws Exception {
        Map<String, Object> textItem = new LinkedHashMap<>();
        textItem.put("type", 1);
        textItem.put("text_item", Map.of("text", text));

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from_user_id", "");
        msg.put("to_user_id", toUserId);
        msg.put("client_id", "jc-claw-" + randomHex(8));
        msg.put("message_type", 2);
        msg.put("message_state", 2);
        if (contextToken != null && !contextToken.isBlank()) {
            msg.put("context_token", contextToken);
        }
        msg.put("item_list", List.of(textItem));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg", msg);
        body.put("base_info", baseInfo());

        HttpRequest req = request("ilink/bot/sendmessage", body, 15);
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("[sendmessage] HTTP {} : {}", resp.statusCode(),
                    resp.body().substring(0, Math.min(200, resp.body().length())));
        } else {
            log.info("[微信发送成功] {}", text.substring(0, Math.min(50, text.length())));
        }
    }

    private void fetchTypingTicket(String contextToken) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ilink_user_id", ilinkUserId);
            body.put("context_token", contextToken);
            body.put("base_info", baseInfo());

            HttpRequest req = request("ilink/bot/getconfig", body, 10);
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(resp.body(), Map.class);
                String ticket = (String) data.get("typing_ticket");
                if (ticket != null && !ticket.isBlank()) {
                    this.typingTicket = ticket;
                    log.info("typing_ticket 获取成功");
                } else {
                    log.warn("getConfig 缺少 typing_ticket, 字段: {}", data.keySet());
                }
            } else {
                log.warn("[getconfig] HTTP {}", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("获取 typing_ticket 失败: {}", e.getMessage());
        }
    }

    private void sendTyping(boolean typing) {
        if (ilinkUserId == null || typingTicket == null) return;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ilink_user_id", ilinkUserId);
            body.put("typing_ticket", typingTicket);
            body.put("status", typing ? 1 : 2);
            body.put("base_info", baseInfo());

            HttpRequest req = request("ilink/bot/sendtyping", body, 5);
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private HttpRequest request(String endpoint, Map<String, Object> body, long timeoutSec) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(ILINK_BASE_URL + "/" + endpoint))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("AuthorizationType", "ilink_bot_token")
                .header("X-WECHAT-UIN", randomWechatUin())
                .header("iLink-App-Id", ILINK_APP_ID)
                .header("iLink-App-ClientVersion", ILINK_APP_CLIENT_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (botToken != null && !botToken.isEmpty()) {
            b.header("Authorization", "Bearer " + botToken);
        }
        return b.build();
    }

    private Map<String, Object> baseInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("channel_version", "2.4.4");
        info.put("bot_agent", "JC-ClawBot");
        return info;
    }

    private static String randomHex(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(Integer.toHexString(ThreadLocalRandom.current().nextInt(16)));
        return sb.toString();
    }

    private static String randomWechatUin() {
        int v = ThreadLocalRandom.current().nextInt();
        return Base64.getEncoder().encodeToString(String.valueOf(v).getBytes());
    }

    private static String toString(Object o) {
        return o instanceof String s ? s : o != null ? o.toString() : "";
    }
}
