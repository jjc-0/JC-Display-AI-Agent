package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class OpenAIProvider implements LLMProvider {

    private final AIConfig aiConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getDefaultModel() {
        return aiConfig.getProviders().getOpenai().getModel();
    }

    @Override
    public CompletableFuture<String> chatCompletion(String systemPrompt, String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode requestBody = buildBaseRequest(systemPrompt);
                ArrayNode messages = (ArrayNode) requestBody.get("messages");
                addMessage(messages, "user", userMessage);
                return executeRequest(requestBody);
            } catch (Exception e) {
                log.error("OpenAI chat completion error", e);
                throw new RuntimeException("OpenAI调用失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> chatCompletionWithHistory(String systemPrompt, List<Map<String, String>> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode requestBody = buildBaseRequest(systemPrompt);
                ArrayNode msgs = (ArrayNode) requestBody.get("messages");
                if (messages != null) {
                    for (Map<String, String> msg : messages) {
                        addMessage(msgs, msg.get("role"), msg.get("content"));
                    }
                }
                return executeRequest(requestBody);
            } catch (Exception e) {
                log.error("OpenAI chat completion with history error", e);
                throw new RuntimeException("OpenAI调用失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> chatCompletionWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode requestBody = buildBaseRequest(systemPrompt);
                ArrayNode messages = (ArrayNode) requestBody.get("messages");
                addMessage(messages, "user", userMessage);
                if (tools != null && !tools.isEmpty()) {
                    ArrayNode toolsArray = objectMapper.valueToTree(tools);
                    requestBody.set("tools", toolsArray);
                    requestBody.put("tool_choice", "auto");
                }
                return executeToolRequest(requestBody);
            } catch (Exception e) {
                log.error("OpenAI tool call error", e);
                throw new RuntimeException("OpenAI工具调用失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> chatCompletionWithToolsAndHistory(String systemPrompt, List<Map<String, String>> messages, List<Map<String, Object>> tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode requestBody = buildBaseRequest(systemPrompt);
                ArrayNode msgs = (ArrayNode) requestBody.get("messages");
                if (messages != null) {
                    for (Map<String, String> msg : messages) {
                        addMessage(msgs, msg.get("role"), msg.get("content"));
                    }
                }
                if (tools != null && !tools.isEmpty()) {
                    ArrayNode toolsArray = objectMapper.valueToTree(tools);
                    requestBody.set("tools", toolsArray);
                    requestBody.put("tool_choice", "auto");
                }
                return executeToolRequest(requestBody);
            } catch (Exception e) {
                log.error("OpenAI tool call with history error", e);
                throw new RuntimeException("OpenAI工具调用失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 图片识别 — 使用 DeepSeek Vision 多模态能力
     * @param imageBytes 图片字节数据
     * @param mimeType  图片 MIME 类型 (image/jpeg, image/png 等)
     * @param prompt    用户提示词
     */
    public CompletableFuture<String> imageRecognition(byte[] imageBytes, String mimeType, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AIConfig.DeepSeekConfig ds = aiConfig.getProviders().getDeepseek();
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                String dataUri = "data:" + mimeType + ";base64," + base64;

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", ds.getModel());
                requestBody.put("max_tokens", ds.getMaxTokens());
                requestBody.put("temperature", ds.getTemperature());

                ArrayNode messages = objectMapper.createArrayNode();
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");

                ArrayNode contentParts = objectMapper.createArrayNode();

                ObjectNode textPart = objectMapper.createObjectNode();
                textPart.put("type", "text");
                textPart.put("text", prompt != null && !prompt.isBlank() ? prompt : "请详细描述这张图片的内容");
                contentParts.add(textPart);

                ObjectNode imagePart = objectMapper.createObjectNode();
                imagePart.put("type", "image_url");
                ObjectNode imageUrl = objectMapper.createObjectNode();
                imageUrl.put("url", dataUri);
                imagePart.set("image_url", imageUrl);
                contentParts.add(imagePart);

                userMsg.set("content", contentParts);
                messages.add(userMsg);
                requestBody.set("messages", messages);

                String url = ds.getBaseUrl() + "/chat/completions";
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                        .addHeader("Authorization", "Bearer " + ds.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        log.error("DeepSeek Vision API error: {} {}", response.code(), errorBody);
                        throw new RuntimeException("图片识别失败: " + response.code() + " " + errorBody);
                    }
                    JsonNode root = objectMapper.readTree(response.body().string());
                    return root.path("choices").get(0).path("message").path("content").asText("");
                }
            } catch (Exception e) {
                log.error("DeepSeek image recognition error", e);
                throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
            }
        });
    }

    private ObjectNode buildBaseRequest(String systemPrompt) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        AIConfig.OpenAIConfig openai = aiConfig.getProviders().getOpenai();
        requestBody.put("model", openai.getModel());
        requestBody.put("max_tokens", openai.getMaxTokens());
        requestBody.put("temperature", openai.getTemperature());
        ArrayNode messages = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            addMessage(messages, "system", systemPrompt);
        }
        requestBody.set("messages", messages);
        return requestBody;
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content != null ? content : "");
        messages.add(msg);
    }

    private String executeRequest(ObjectNode requestBody) throws IOException {
        JsonNode messageNode = callApi(requestBody);
        return messageNode.path("content").asText("");
    }

    private String executeToolRequest(ObjectNode requestBody) throws IOException {
        JsonNode messageNode = callApi(requestBody);

        JsonNode toolCalls = messageNode.path("tool_calls");
        if (toolCalls.isArray() && toolCalls.size() > 0) {
            JsonNode firstToolCall = toolCalls.get(0);
            JsonNode function = firstToolCall.path("function");
            String name = function.path("name").asText();
            String arguments = function.path("arguments").asText();

            ObjectNode toolCallResult = objectMapper.createObjectNode();
            toolCallResult.put("name", name);
            try {
                toolCallResult.set("arguments", objectMapper.readTree(arguments));
            } catch (Exception e) {
                toolCallResult.put("arguments", arguments);
            }

            String content = messageNode.path("content").asText(null);
            if (content != null && !content.isBlank()) {
                return content + "\n" + toolCallResult.toString();
            }
            return toolCallResult.toString();
        }

        return messageNode.path("content").asText("");
    }

    private JsonNode callApi(ObjectNode requestBody) throws IOException {
        AIConfig.OpenAIConfig openai = aiConfig.getProviders().getOpenai();
        String url = openai.getBaseUrl() + "/chat/completions";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + openai.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("OpenAI API error: {} {}", response.code(), errorBody);
                throw new RuntimeException("OpenAI API返回错误: " + response.code() + " " + errorBody);
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path("choices").get(0).path("message");
        }
    }
}
