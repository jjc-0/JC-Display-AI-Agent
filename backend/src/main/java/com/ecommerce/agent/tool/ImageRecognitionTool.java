package com.ecommerce.agent.tool;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.llm.MultiModelOrchestrator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 多模态识图工具 — DeepSeek Vision 优先，ChatGPT 兜底
 */
@Slf4j
@Component
public class ImageRecognitionTool implements Tool {

    private final AgentRuntime agentRuntime;
    private final MultiModelOrchestrator orchestrator;
    private final AIConfig aiConfig;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public ImageRecognitionTool(@Lazy AgentRuntime agentRuntime,
                                MultiModelOrchestrator orchestrator,
                                AIConfig aiConfig) {
        this.agentRuntime = agentRuntime;
        this.orchestrator = orchestrator;
        this.aiConfig = aiConfig;
    }

    @Override public String getName() { return "image_understand"; }
    @Override public String getCategory() { return "MULTIMODAL"; }
    @Override public long getTimeoutMs() { return 300000; }

    @Override
    public boolean isEnabled() {
        return aiConfig.isDeepSeekKeyConfigured() || aiConfig.isOpenAIKeyConfigured();
    }

    @Override
    public String getDescription() {
        return "仅当用户明确要求分析图片时才调用。对图片内容进行识别——产品特征、颜色、材质等。" +
                "支持JPG/PNG。可通过数字索引(0,1,2...)引用用户上传的图片。注意：用户仅发送图片不等于要求识图。";
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
        // 同步执行（ToolRouter 管理线程；避免 ForkJoinPool 嵌套死锁）
        try {
            if (!isEnabled()) return done("❌ 识图服务未配置API Key");

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
                        return done("❌ 图片索引" + idx + " 超出范围");
                    }
                }
            }

            if (imageUrl == null || imageUrl.isBlank()) return done("❌ 请提供图片URL或索引");

            String mimeType = "image/jpeg";
            byte[] imageBytes;

            if (imageUrl.startsWith("data:")) {
                String[] parts = imageUrl.split(",");
                if (parts.length < 2) return done("❌ 无效的 Base64 数据");
                String header = parts[0];
                if (header.contains(";")) {
                    mimeType = header.substring(5, header.indexOf(";"));
                }
                imageBytes = Base64.getDecoder().decode(parts[1]);
            } else if (imageUrl.startsWith("http")) {
                Request req = new Request.Builder().url(imageUrl).get().build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (!resp.isSuccessful()) return done("❌ 图片下载失败: HTTP " + resp.code());
                    imageBytes = resp.body() != null ? resp.body().bytes() : new byte[0];
                    mimeType = resp.header("Content-Type", "image/jpeg");
                }
            } else {
                return done("❌ 不支持的图片格式");
            }

            // DeepSeek Vision 优先，失败自动回退 ChatGPT（带超时防护）
            String result = orchestrator.recognizeImage(imageBytes, mimeType, prompt)
                    .get(280, java.util.concurrent.TimeUnit.SECONDS);
            return done("🖼️ AI 视觉识别结果:\n\n" + result);

        } catch (Exception e) {
            log.error("识图失败", e);
            return done("❌ 图片识别失败: " + e.getMessage());
        }
    }

    private CompletableFuture<String> done(String s) {
        return CompletableFuture.completedFuture(s);
    }


    @SuppressWarnings("unchecked")
    private String findSessionIdInContext(Map<String, Object> params) {
        Object ctx = params.get("_chain_context");
        if (ctx instanceof Map<?, ?> map) return (String) map.get("session_id");
        return null;
    }
}
