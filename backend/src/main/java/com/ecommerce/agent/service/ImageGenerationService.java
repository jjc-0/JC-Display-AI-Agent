package com.ecommerce.agent.service;

import com.ecommerce.agent.config.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 图片生成服务 — 同步调用，base64 存储到本地文件
 *
 * 双路径策略：
 *   1. /v1/images/generations + gpt-image-2 (专用图片模型)
 *   2. 失败 → /v1/chat/completions + chat 模型 (兜底)
 *
 * b64_json → 解码存本地 → 返回 /uploads/xxx.png (避免 DB 截断)
 */
@Slf4j
@Service
public class ImageGenerationService {

    private final AIConfig aiConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final Path UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();

    static {
        try { Files.createDirectories(UPLOAD_DIR); } catch (IOException ignored) {}
    }

    public ImageGenerationService(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return aiConfig.getProviders().getImageGen().isEnabled()
                && aiConfig.isOpenAIKeyConfigured();
    }

    // ════════════════════════════════════════
    // 图片编辑：原图 + 修改指令 → 新图
    // ════════════════════════════════════════
    public Map<String, Object> edit(byte[] originalImage, String mimeType, String editPrompt) {
        AIConfig.OpenAIConfig oai = aiConfig.getProviders().getOpenai();
        String imgModel = aiConfig.getProviders().getImageGen().getModel();
        log.info("═══ 图片编辑开始 ═══ model={} prompt={}", imgModel,
                editPrompt.substring(0, Math.min(50, editPrompt.length())));

        // 路径1: /v1/images/edits (OpenAI 官方编辑端点)
        try { return editViaEditsEndpoint(oai, imgModel, originalImage, mimeType, editPrompt); }
        catch (Exception e1) {
            log.warn("路径1(images/edits)失败: {} → 尝试路径2", e1.getMessage());
            try { return editViaChatApi(oai, originalImage, mimeType, editPrompt); }
            catch (Exception e2) {
                throw new RuntimeException("图片编辑失败: " + e2.getMessage(), e2);
            }
        }
    }

    private Map<String, Object> editViaEditsEndpoint(
            AIConfig.OpenAIConfig oai, String model, byte[] imageBytes,
            String mimeType, String editPrompt) throws Exception {

        String imageFileName = mimeType.contains("png") ? "image.png" : "image.jpg";
        RequestBody imageBody = RequestBody.create(imageBytes, MediaType.parse(mimeType));

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFileName, imageBody)
                .addFormDataPart("prompt", editPrompt)
                .addFormDataPart("model", model)
                .addFormDataPart("n", "1")
                .addFormDataPart("size", "1024x1024")
                .addFormDataPart("response_format", "b64_json")
                .build();

        String url = oai.getBaseUrl() + "/images/edits";
        Request req = new Request.Builder().url(url).post(body)
                .addHeader("Authorization", "Bearer " + oai.getApiKey()).build();

        long t0 = System.currentTimeMillis();
        String respBody; int code;
        try (Response r = httpClient.newCall(req).execute()) {
            code = r.code(); respBody = r.body() != null ? r.body().string() : "";
        }
        log.info("← edits HTTP{} ({}B {}ms)", code, respBody.length(), System.currentTimeMillis() - t0);

        if (code < 200 || code >= 300)
            throw new RuntimeException("HTTP " + code + ": " + respBody.substring(0, Math.min(300, respBody.length())));

        JsonNode root = objectMapper.readTree(respBody);
        JsonNode data = root.path("data");
        if (!data.isArray()) throw new RuntimeException("edits端点未返回 data 数组");

        List<String> images = new ArrayList<>();
        for (JsonNode item : data) {
            String b64 = item.path("b64_json").asText(null);
            if (b64 != null && !b64.isBlank()) {
                images.add(saveBase64Image(b64, editPrompt)); continue;
            }
            String imgUrl = item.path("url").asText(null);
            if (imgUrl != null && !imgUrl.isBlank()) images.add(imgUrl);
        }
        if (images.isEmpty()) throw new RuntimeException("edits端点 data[] 无图片数据");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("images", images);
        return r;
    }

    private Map<String, Object> editViaChatApi(
            AIConfig.OpenAIConfig oai, byte[] imageBytes,
            String mimeType, String editPrompt) throws Exception {

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:" + mimeType + ";base64," + base64;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", oai.getModel()); body.put("max_tokens", 8192);

        ArrayNode msgs = objectMapper.createArrayNode();
        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system").put("content",
                "You are an image editor. The user provides an image + edit instructions. "
                + "Describe the edited result OR return a generated image markdown.");
        msgs.add(sys);

        ObjectNode usr = objectMapper.createObjectNode();
        usr.put("role", "user");
        ArrayNode parts = objectMapper.createArrayNode();
        ObjectNode tp = objectMapper.createObjectNode();
        tp.put("type", "text").put("text", "对图片进行以下修改：" + editPrompt);
        parts.add(tp);
        ObjectNode ip = objectMapper.createObjectNode();
        ip.put("type", "image_url");
        ObjectNode iu = objectMapper.createObjectNode();
        iu.put("url", dataUri);
        ip.set("image_url", iu);
        parts.add(ip);
        usr.set("content", parts);
        msgs.add(usr);
        body.set("messages", msgs);

        String url = oai.getBaseUrl() + "/chat/completions";
        Request req = new Request.Builder().url(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + oai.getApiKey())
                .addHeader("Content-Type", "application/json").build();

        String respBody; int code;
        try (Response r = httpClient.newCall(req).execute()) {
            code = r.code(); respBody = r.body() != null ? r.body().string() : "";
        }
        if (code < 200 || code >= 300)
            throw new RuntimeException("HTTP " + code + ": " + respBody.substring(0, Math.min(300, respBody.length())));

        JsonNode root = objectMapper.readTree(respBody);
        List<String> images = new ArrayList<>();
        for (JsonNode ch : root.path("choices")) {
            for (JsonNode gi : ch.path("generated_images")) {
                String b = gi.path("b64_json").asText(null);
                if (b != null && !b.isBlank()) { images.add(saveBase64Image(b, editPrompt)); continue; }
                String u = gi.path("url").asText(null);
                if (u != null && u.startsWith("http")) images.add(u);
            }
            JsonNode ct = ch.path("message").path("content");
            if (ct.isArray()) {
                for (JsonNode p : ct) {
                    String b = p.path("b64_json").asText(null);
                    if (b != null && !b.isBlank()) images.add(saveBase64Image(b, editPrompt));
                    String u = p.path("image_url").path("url").asText(null);
                    if (u == null) u = p.path("url").asText(null);
                    if (u != null && u.startsWith("http")) images.add(u);
                }
            }
            if (ct.isTextual() && images.isEmpty()) images.add(ct.asText());
        }
        if (images.isEmpty())
            throw new RuntimeException("Chat 响应未包含图片: " + respBody.substring(0, Math.min(200, respBody.length())));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("images", images);
        return r;
    }

    /** 文字 → 图片（同步） */
    public Map<String, Object> generate(String prompt, String style, String size) {
        AIConfig.OpenAIConfig oai = aiConfig.getProviders().getOpenai();
        String imgModel = aiConfig.getProviders().getImageGen().getModel();
        String imgSize = (size != null) ? size : aiConfig.getProviders().getImageGen().getSize();

        log.info("═══ 图片生成开始 ═══ model={} size={}", imgModel, imgSize);

        try { return generateViaImagesEndpoint(oai, imgModel, prompt, imgSize); }
        catch (Exception e1) {
            log.warn("路径1 images端点失败: {}", e1.getMessage());
            try { return generateViaChatApi(oai, prompt, imgSize); }
            catch (Exception e2) {
                throw new RuntimeException("图片生成失败。Images: " + e1.getMessage()
                        + "  Chat: " + e2.getMessage(), e2);
            }
        }
    }

    // ════════════════════════════════════════
    // 路径1 /v1/images/generations
    // ════════════════════════════════════════
    private Map<String, Object> generateViaImagesEndpoint(
            AIConfig.OpenAIConfig oai, String imgModel, String prompt, String size) throws Exception {

        String ns = size != null ? size.replace('*', 'x') : "1024x1024";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", imgModel);
        body.put("prompt", prompt);
        body.put("n", aiConfig.getProviders().getImageGen().getN());
        body.put("size", ns);

        String url = oai.getBaseUrl() + "/images/generations";
        Request req = new Request.Builder().url(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + oai.getApiKey())
                .addHeader("Content-Type", "application/json").build();

        String respBody; int code; long t0 = System.currentTimeMillis();
        try (Response r = httpClient.newCall(req).execute()) {
            code = r.code();
            respBody = r.body() != null ? r.body().string() : "";
        }
        log.info("← 路径1 HTTP{} ({}B {}ms)", code, respBody.length(), System.currentTimeMillis() - t0);

        if (code < 200 || code >= 300)
            throw new RuntimeException("HTTP " + code + ": " + respBody.substring(0, Math.min(300, respBody.length())));

        JsonNode root = objectMapper.readTree(respBody);
        JsonNode data = root.path("data");
        if (!data.isArray()) throw new RuntimeException("images端点未返回 data 数组");

        List<String> images = new ArrayList<>();
        for (JsonNode item : data) {
            // b64_json → 存本地文件
            String b64 = item.path("b64_json").asText(null);
            if (b64 != null && !b64.isBlank()) {
                images.add(saveBase64Image(b64, prompt));
                continue;
            }
            String imgUrl = item.path("url").asText(null);
            if (imgUrl != null && !imgUrl.isBlank()) images.add(imgUrl);
        }
        if (images.isEmpty())
            throw new RuntimeException("images端点 data[] 无图片数据");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("images", images);
        log.info("✅ 路径1成功: {} 张", images.size());
        return r;
    }

    // ════════════════════════════════════════
    // 路径2 /v1/chat/completions
    // ════════════════════════════════════════
    private Map<String, Object> generateViaChatApi(
            AIConfig.OpenAIConfig oai, String prompt, String size) throws Exception {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", oai.getModel()); body.put("max_tokens", 8192);
        ArrayNode msgs = objectMapper.createArrayNode();
        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system").put("content", "You are an image generator. Output as markdown image link.");
        msgs.add(sys);
        ObjectNode usr = objectMapper.createObjectNode();
        usr.put("role", "user").put("content", "Generate: " + prompt);
        msgs.add(usr);
        body.set("messages", msgs);

        String url = oai.getBaseUrl() + "/chat/completions";
        Request req = new Request.Builder().url(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + oai.getApiKey())
                .addHeader("Content-Type", "application/json").build();

        String respBody; int code;
        try (Response r = httpClient.newCall(req).execute()) {
            code = r.code();
            respBody = r.body() != null ? r.body().string() : "";
        }
        log.info("← 路径2 HTTP{} ({}B)", code, respBody.length());

        if (code < 200 || code >= 300)
            throw new RuntimeException("HTTP " + code + ": " + respBody.substring(0, Math.min(500, respBody.length())));

        JsonNode root = objectMapper.readTree(respBody);
        List<String> images = new ArrayList<>();
        for (JsonNode ch : root.path("choices")) {
            // generated_images
            for (JsonNode gi : ch.path("generated_images")) {
                String b = gi.path("b64_json").asText(null);
                if (b != null && !b.isBlank()) { images.add(saveBase64Image(b, prompt)); continue; }
                String u = gi.path("url").asText(null);
                if (u != null && u.startsWith("http")) images.add(u);
            }
            // content 数组
            JsonNode ct = ch.path("message").path("content");
            if (ct.isArray()) {
                for (JsonNode p : ct) {
                    String b = p.path("b64_json").asText(null);
                    if (b != null && !b.isBlank()) images.add(saveBase64Image(b, prompt));
                    String u = p.path("image_url").path("url").asText(null);
                    if (u == null) u = p.path("url").asText(null);
                    if (u != null && u.startsWith("http")) images.add(u);
                }
            }
            // content 文本
            if (ct.isTextual() && images.isEmpty()) images.add(ct.asText());
        }
        if (images.isEmpty())
            throw new RuntimeException("Chat 响应未包含图片。Body: " + respBody.substring(0, Math.min(300, respBody.length())));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("images", images);
        return r;
    }

    // ════════════════════════════════════════
    // 本地文件存储
    // ════════════════════════════════════════
    private String saveBase64Image(String b64, String prompt) {
        try {
            byte[] decoded = Base64.getDecoder().decode(b64);
            String hash = sha256Hex(decoded).substring(0, 12);
            String filename = hash + ".png";
            Path file = UPLOAD_DIR.resolve(filename);
            if (!Files.exists(file)) Files.write(file, decoded);
            String localUrl = "/uploads/" + filename;
            log.info("  base64保存: {} ({} bytes)", localUrl, decoded.length);
            return localUrl;
        } catch (Exception e) {
            log.error("保存base64图片失败", e);
            // 降级：返回 data URI（小图能撑住）
            return "data:image/png;base64," + b64;
        }
    }

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
