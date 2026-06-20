package com.ecommerce.agent.service;

import com.ecommerce.agent.config.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ImageGenerationService {

    private final AIConfig aiConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ImageGenerationService(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        AIConfig.ImageGenConfig cfg = aiConfig.getProviders().getImageGen();
        if (!cfg.isEnabled()) return false;
        String key = cfg.getApiKey();
        return key != null && !key.isBlank() && !key.contains("placeholder") && !key.contains("your-key");
    }

    /**
     * 文字生成产品图片 — DashScope 通义万象
     */
    public CompletableFuture<Map<String, Object>> generate(String prompt, String style, String size) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AIConfig.ImageGenConfig cfg = aiConfig.getProviders().getImageGen();

                // 1. 提交生成任务
                String taskId = submitTask(cfg, prompt, style, size);
                log.info("图片生成任务已提交: taskId={}", taskId);

                // 2. 轮询等待完成
                List<String> imageUrls = pollTask(cfg, taskId);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("images", imageUrls);
                log.info("图片生成成功: {} 张", imageUrls.size());
                return result;

            } catch (Exception e) {
                log.error("图片生成失败", e);
                throw new RuntimeException("图片生成失败: " + e.getMessage(), e);
            }
        });
    }

    private String submitTask(AIConfig.ImageGenConfig cfg, String prompt, String style, String size) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.getModel());

        ObjectNode input = objectMapper.createObjectNode();
        input.put("prompt", prompt);
        body.set("input", input);

        ObjectNode params = objectMapper.createObjectNode();
        String dsSize = resolveSize(size);
        if (dsSize != null) params.put("size", dsSize);
        params.put("n", cfg.getN());
        params.put("ref_mode", "repaint");
        body.set("parameters", params);

        String url = cfg.getBaseUrl() + "/api/v1/services/aigc/text2image/image-synthesis";
        Request request = buildRequest(url, body);
        String respBody = execute(request);
        JsonNode root = objectMapper.readTree(respBody);

        String taskId = root.path("output").path("task_id").asText();
        String status = root.path("output").path("task_status").asText();
        if (taskId.isEmpty()) {
            String code = root.path("code").asText("");
            String msg = root.path("message").asText("Unknown error");
            throw new RuntimeException("提交任务失败: " + code + " " + msg);
        }
        log.info("任务状态: {}, taskId: {}", status, taskId);
        return taskId;
    }

    private List<String> pollTask(AIConfig.ImageGenConfig cfg, String taskId) throws Exception {
        int maxRetries = 60;
        int interval = 2000;

        for (int i = 0; i < maxRetries; i++) {
            Thread.sleep(interval);

            String url = cfg.getBaseUrl() + "/api/v1/tasks/" + taskId;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + cfg.getApiKey())
                    .build();

            String respBody = execute(request);
            JsonNode root = objectMapper.readTree(respBody);

            String status = root.path("output").path("task_status").asText();
            log.info("轮询 {}/{}: status={}", i + 1, maxRetries, status);

            switch (status) {
                case "SUCCEEDED": {
                    List<String> urls = new ArrayList<>();
                    JsonNode results = root.path("output").path("results");
                    if (results.isArray()) {
                        for (JsonNode r : results) {
                            String imgUrl = r.path("url").asText(null);
                            if (imgUrl != null && !imgUrl.isBlank()) {
                                urls.add(imgUrl);
                            }
                        }
                    }
                    return urls;
                }
                case "FAILED":
                    String code = root.path("output").path("code").asText("");
                    String msg = root.path("output").path("message").asText("Unknown error");
                    throw new RuntimeException("图片生成任务失败: " + code + " " + msg);
                case "PENDING":
                case "RUNNING":
                    break;
                default:
                    throw new RuntimeException("未知任务状态: " + status);
            }
        }
        throw new RuntimeException("图片生成超时，请稍后重试");
    }

    private Request buildRequest(String url, ObjectNode body) {
        String key = aiConfig.getProviders().getImageGen().getApiKey();
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + key)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-DashScope-Async", "enable")
                .build();
    }

    private String execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("API error {}: {}", response.code(), body);
                throw new RuntimeException("API返回错误 (HTTP " + response.code() + "): " + body);
            }
            return body;
        }
    }

    private String resolveSize(String size) {
        if (size == null) return null;
        return switch (size) {
            case "1024x1024" -> "1024*1024";
            case "1792x1024" -> "1664*928";
            case "1024x1792" -> "928*1664";
            default -> size.replace('x', '*');
        };
    }
}
