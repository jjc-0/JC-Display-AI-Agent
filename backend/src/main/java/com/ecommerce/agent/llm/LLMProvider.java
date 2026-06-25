package com.ecommerce.agent.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface LLMProvider {

    String getProviderName();

    CompletableFuture<String> chatCompletion(String systemPrompt, String userMessage);

    CompletableFuture<String> chatCompletionWithHistory(String systemPrompt, List<Map<String, String>> messages);

    CompletableFuture<String> chatCompletionWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools);

    /** v2: 带历史 + 工具 — 支持多轮 tool call */
    CompletableFuture<String> chatCompletionWithToolsAndHistory(String systemPrompt, List<Map<String, String>> messages, List<Map<String, Object>> tools);

    /** 第一轮对话：带工具 + 图片（多模态）— 图片直接作为 content parts 传入 */
    CompletableFuture<String> chatCompletionWithToolsAndImages(String systemPrompt, String userMessage,
            List<Map<String, Object>> tools, List<String> base64Images);

    String getDefaultModel();
}
