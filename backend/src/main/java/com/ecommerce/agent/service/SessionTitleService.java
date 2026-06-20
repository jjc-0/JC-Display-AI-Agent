package com.ecommerce.agent.service;

import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.llm.MultiModelOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class SessionTitleService {

    private final MultiModelOrchestrator orchestrator;
    private final ConversationManager conversationManager;
    private final AIConfig aiConfig;

    public SessionTitleService(MultiModelOrchestrator orchestrator,
                               ConversationManager conversationManager,
                               AIConfig aiConfig) {
        this.orchestrator = orchestrator;
        this.conversationManager = conversationManager;
        this.aiConfig = aiConfig;
    }

    public void autoTitle(String sessionId, String firstUserMessage) {
        if (firstUserMessage == null || firstUserMessage.isBlank()) return;

        String fallbackTitle = firstUserMessage.length() > 30
                ? firstUserMessage.substring(0, 30).replace('\n', ' ')
                : firstUserMessage.replace('\n', ' ');
        conversationManager.updateSessionTitle(sessionId, fallbackTitle);

        if (!aiConfig.isDeepSeekKeyConfigured()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String aiTitle = generateTitleViaLLM(firstUserMessage);
                if (aiTitle != null && !aiTitle.isBlank()) {
                    conversationManager.updateSessionTitle(sessionId, aiTitle);
                    log.debug("AI自动命名: sessionId={}, title={}", sessionId.substring(0, 8), aiTitle);
                }
            } catch (Exception e) {
                log.warn("AI自动命名失败: {}", e.getMessage());
            }
        });
    }

    private String generateTitleViaLLM(String message) {
        try {
            String systemPrompt = """
                    你是一个对话标题生成器。根据用户的对话内容，生成一个简短的标题。
                    要求：
                    - 10个汉字或20个英文字符以内
                    - 只返回标题文字，不要任何标点符号、引号或额外解释
                    - 提炼对话的核心主题
                    - 如果对话是翻译任务，用"翻译: "开头
                    - 如果对话是分析任务，用"分析: "开头
                    - 如果对话是文案任务，用"文案: "开头
                    """;
            String title = orchestrator.reasoning(systemPrompt, "为以下对话生成标题:\n" + message).get();
            if (title != null) {
                title = title.trim().replaceAll("^[\"']|[\"']$", "");
                if (title.length() > 30) {
                    title = title.substring(0, 30);
                }
            }
            return title;
        } catch (Exception e) {
            log.warn("LLM标题生成失败: {}", e.getMessage());
            return null;
        }
    }
}
