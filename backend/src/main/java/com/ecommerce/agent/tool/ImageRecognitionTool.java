package com.ecommerce.agent.tool;

import com.ecommerce.agent.agent.AgentRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 多模态识图工具 — DeepSeek Vision
 *
 * 使用 DeepSeek API 进行视觉分析（deepseek-chat 支持多模态 image_url）。
 * 通过 OpenAI 兼容接口调用: https://api.deepseek.com/v1
 *
 * 支持: URL下载 / Base64 DataURI / 会话图片索引
 */
@Slf4j
@Component
public class ImageRecognitionTool implements Tool {

    private final AgentRuntime agentRuntime;

    @Value("${DEEPSEEK_API_KEY:sk-placeholder}")
    private String apiKey;

    @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com/v1}")
    private String baseUrl;

    /** DeepSeek 多模态模型 */
    private static final String VISION_MODEL = "deepseek-chat";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageRecognitionTool(@Lazy AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @Override public String getName() { return "image_understand"; }
    @Override public String getCategory() { return "MULTIMODAL"; }
    @Override public long getTimeoutMs() { return 120000; }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("placeholder");
    }

    @Override
    public String getDescription() {
        return "AI视觉识别工具（DeepSeek多模态）。分析图片内容——产品特征、颜色、材质、竞品包装、生产工艺等。支持JPG/PNG/GIF/WebP。可通过数字索引(0,1,2...)引用用户上传的图片。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> urlProp = new LinkedHashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "图片URL(http/https/data:...) 或数字索引(0/1/2...)引用用户上传的图片");
        props.put("image_url", urlProp);

        Map<String, Object> promptProp = new LinkedHashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "分析提示词");
        props.put("prompt", promptProp);

        Map<String, Object> sidProp = new LinkedHashMap<>();
        sidProp.put("type", "string");
        sidProp.put("description", "会话ID，用于查找用户上传的图片");
        props.put("session_id", sidProp);

        schema.put("properties", props);
        schema.put("required", List.of("image_url"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isEnabled()) return "❌ 识图服务未配置API Key";

                String imageUrl = (String) params.get("image_url");
                String prompt = (String) params.getOrDefault("prompt",
                        "请详细描述这张图片的内容，产品特征、颜色、材质、用途、设计特点");
                String sessionId = (String) params.get("session_id");

                // 数字索引 → 从 AgentRuntime 会话图片中查找
                if (imageUrl != null && imageUrl.trim().matches("\\d+")) {
                    int idx = Integer.parseInt(imageUrl.trim());
                    if (sessionId == null) sessionId = findSessionIdInContext(params);
                    if (sessionId != null) {
                        List<String> imgs = agentRuntime.getSessionImages(sessionId);
                        if (idx >= 0 && idx < imgs.size()) {
                            imageUrl = imgs.get(idx);
                            log.info("识图: 从会话获取图片[{}]", idx);
                        } else {
                            return "❌ 图片索引" + idx + " 超出范围";
                        }
                    }
                }

                if (imageUrl == null || imageUrl.isBlank()) return "❌ 请提供图片URL或索引";

                String dataUri;
                if (imageUrl.startsWith("data:")) {
                    dataUri = imageUrl;
                } else if (imageUrl.startsWith("http")) {
                    Request req = new Request.Builder().url(imageUrl).get().build();
                    try (Response resp = httpClient.newCall(req).execute()) {
                        if (!resp.isSuccessful()) return "❌ 图片下载失败: HTTP " + resp.code();
                        byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
                        String mime = resp.header("Content-Type", "image/jpeg");
                        String b64 = Base64.getEncoder().encodeToString(bytes);
                        dataUri = "data:" + mime + ";base64," + b64;
                    }
                } else {
                    return "❌ 不支持的图片格式";
                }

                // 调用 DeepSeek 多模态 (OpenAI 兼容模式)
                String result = callVisionModel(dataUri, prompt);
                return "🖼️ AI 视觉识别结果:\n\n" + result;

            } catch (Exception e) {
                log.error("识图失败", e);
                return "❌ 图片识别失败: " + e.getMessage();
            }
        });
    }

    private String callVisionModel(String dataUri, String prompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", VISION_MODEL);
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.7);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");

        ArrayNode contentParts = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        contentParts.add(textPart);

        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrlNode = objectMapper.createObjectNode();
        imageUrlNode.put("url", dataUri);
        imagePart.set("image_url", imageUrlNode);
        contentParts.add(imagePart);

        userMsg.set("content", contentParts);
        messages.add(userMsg);
        requestBody.set("messages", messages);

        String url = baseUrl + "/chat/completions";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        log.info("识图请求: model={}, prompt={}...", VISION_MODEL, prompt.substring(0, Math.min(40, prompt.length())));

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("DeepSeek Vision API错误: {} {}", response.code(), errBody);
                throw new RuntimeException("DeepSeek Vision调用失败: " + response.code() + " " + errBody);
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path("choices").get(0).path("message").path("content").asText("");
        }
    }

    @SuppressWarnings("unchecked")
    private String findSessionIdInContext(Map<String, Object> params) {
        Object ctx = params.get("_chain_context");
        if (ctx instanceof Map<?, ?> map) return (String) map.get("session_id");
        return null;
    }
}
