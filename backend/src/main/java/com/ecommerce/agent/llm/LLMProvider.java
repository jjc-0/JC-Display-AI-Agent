package com.ecommerce.agent.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface LLMProvider {

    String getProviderName();

    CompletableFuture<String> chatCompletion(String systemPrompt, String userMessage);

    CompletableFuture<String> chatCompletionWithHistory(String systemPrompt, List<Map<String, String>> messages);

    CompletableFuture<String> chatCompletionWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools);

    String getDefaultModel();
}
