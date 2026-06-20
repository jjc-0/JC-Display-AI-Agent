package com.ecommerce.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class MultiModelOrchestrator {

    private final DeepSeekProvider deepSeekProvider;

    public MultiModelOrchestrator(DeepSeekProvider deepSeekProvider) {
        this.deepSeekProvider = deepSeekProvider;
    }

    public LLMProvider getProvider() {
        return deepSeekProvider;
    }

    public CompletableFuture<String> reasoning(String systemPrompt, String userMessage) {
        return deepSeekProvider.chatCompletion(systemPrompt, userMessage);
    }

    public CompletableFuture<String> chatWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools) {
        return deepSeekProvider.chatCompletionWithTools(systemPrompt, userMessage, tools);
    }
}
