package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AIConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多模型编排器
 *
 * 策略: DeepSeek 优先，ChatGPT 仅用于 DeepSeek 不支持的多模态/图片生成场景
 *  - 纯文本对话 → 永远使用 DeepSeek
 *  - 图片识别/多模态 → DeepSeek Vision 优先，失败/不可用时回退 ChatGPT
 *  - 图片生成 → 使用 ChatGPT DALL-E
 */
@Slf4j
@Component
public class MultiModelOrchestrator {

    private final DeepSeekProvider deepSeekProvider;
    private final OpenAIProvider openAIProvider;
    private final AIConfig aiConfig;

    public MultiModelOrchestrator(DeepSeekProvider deepSeekProvider,
                                  OpenAIProvider openAIProvider,
                                  AIConfig aiConfig) {
        this.deepSeekProvider = deepSeekProvider;
        this.openAIProvider = openAIProvider;
        this.aiConfig = aiConfig;
    }

    /** 主 Provider: 始终返回 DeepSeek（纯文本任务） */
    public LLMProvider getProvider() {
        return deepSeekProvider;
    }

    /** DeepSeek 兜底 Provider: 多模态/图片相关任务的回退 */
    public LLMProvider getFallbackProvider() {
        return openAIProvider;
    }

    /** 是否可回退到 OpenAI */
    public boolean canFallbackToOpenAI() {
        return aiConfig.isOpenAIKeyConfigured();
    }

    public CompletableFuture<String> reasoning(String systemPrompt, String userMessage) {
        return deepSeekProvider.chatCompletion(systemPrompt, userMessage);
    }

    public CompletableFuture<String> chatWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools) {
        return deepSeekProvider.chatCompletionWithTools(systemPrompt, userMessage, tools);
    }

    /**
     * 多模态对话 — 图片相关直接用 OpenAI（DeepSeek 不支持 image_url，省去无效重试）
     */
    public CompletableFuture<String> chatWithToolsAndImages(
            String systemPrompt, String userMessage, List<Map<String, Object>> tools, List<String> images) {
        log.info("图片多模态 → 直走 OpenAI");
        if (!canFallbackToOpenAI()) {
            throw new RuntimeException("OpenAI 未配置，无法处理多模态图片");
        }
        return openAIProvider.chatCompletionWithToolsAndImages(systemPrompt, userMessage, tools, images);
    }

    /**
     * 图片识别 — 直走 OpenAI（DeepSeek deepseek-chat 不支持 Vision）
     */
    public CompletableFuture<String> recognizeImage(byte[] imageBytes, String mimeType, String prompt) {
        if (!canFallbackToOpenAI()) {
            throw new RuntimeException("OpenAI 未配置，无法识别图片");
        }
        log.info("图片识别 → 直走 OpenAI");
        return openAIProvider.imageRecognition(imageBytes, mimeType, prompt);
    }
}
