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
        // 微信会话已通过 WeChatBotService 固定标题，不覆盖
        if (sessionId != null && sessionId.startsWith("wx_")) return;

        // 先用简短摘要作为占位标题（等待 AI 命名）
        String abbrevTitle = abbreviate(firstUserMessage);
        conversationManager.updateSessionTitle(sessionId, abbrevTitle);

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

    /** 生成简洁摘要作为占位标题（避免原始消息中的乱码/长文本） */
    private String abbreviate(String msg) {
        String cleaned = msg.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        // 按 emoji/标点取前 20 个字符
        String shortText = cleaned.length() > 20 ? cleaned.substring(0, 20).trim() + "..." : cleaned;
        // 如果全是英文/混合，取前 30 字符
        if (cleaned.matches(".*[a-zA-Z].*")) {
            shortText = cleaned.length() > 30 ? cleaned.substring(0, 30).trim() + "..." : cleaned;
        }
        return shortText;
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
