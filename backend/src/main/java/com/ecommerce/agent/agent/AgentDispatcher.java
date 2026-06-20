package com.ecommerce.agent.agent;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.llm.LLMProvider;
import com.ecommerce.agent.llm.MultiModelOrchestrator;
import com.ecommerce.agent.llm.PromptTemplateManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.model.ConversationMessage;
import com.ecommerce.agent.rag.RAGService;
import com.ecommerce.agent.service.SessionTitleService;
import com.ecommerce.agent.tool.Tool;
import com.ecommerce.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AgentDispatcher {

    private final MultiModelOrchestrator orchestrator;
    private final PromptTemplateManager promptManager;
    private final ConversationManager conversationManager;
    private final ToolRegistry toolRegistry;
    private final AIConfig aiConfig;
    private final RAGService ragService;
    private final SessionTitleService titleService;
    private final ObjectMapper objectMapper;

    public AgentDispatcher(MultiModelOrchestrator orchestrator,
                           PromptTemplateManager promptManager,
                           ConversationManager conversationManager,
                           ToolRegistry toolRegistry,
                           AIConfig aiConfig,
                           RAGService ragService,
                           SessionTitleService titleService) {
        this.orchestrator = orchestrator;
        this.promptManager = promptManager;
        this.conversationManager = conversationManager;
        this.toolRegistry = toolRegistry;
        this.aiConfig = aiConfig;
        this.ragService = ragService;
        this.titleService = titleService;
        this.objectMapper = new ObjectMapper();
    }

    public AgentResponse dispatch(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = ensureSession(request.getSessionId(), toOperationType(request.getTaskType()));
        boolean isNewSession = request.getSessionId() == null || !conversationManager.sessionExists(request.getSessionId());

        conversationManager.addMessage(sessionId, "user", request.getMessage());

        if (isNewSession) {
            titleService.autoTitle(sessionId, request.getMessage());
        }

        if (!aiConfig.isDeepSeekKeyConfigured()) {
            String demoMsg = buildDemoResponse(request);
            conversationManager.addMessage(sessionId, "assistant", demoMsg, "demo-mode",
                    System.currentTimeMillis() - startTime);
            return AgentResponse.builder()
                    .sessionId(sessionId)
                    .message(demoMsg)
                    .taskType(request.getTaskType())
                    .status("success")
                    .toolCalls(Collections.emptyList())
                    .metadata(Map.of("mode", "demo", "contextSize", 1))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .modelUsed("demo-mode")
                    .build();
        }

        LLMProvider provider = orchestrator.getProvider();
        String modelUsed = provider.getDefaultModel();

        if (request.isEnableTools()) {
            return dispatchWithTools(request, sessionId, provider, modelUsed, startTime);
        }

        String systemPrompt = buildSystemPrompt(request);
        String ragAugmented = ragService.augmentPrompt(request.getMessage());
        String effectiveMessage = ragAugmented != null ? ragAugmented : request.getMessage();

        String response;
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            List<Map<String, String>> fullHistory = new ArrayList<>();
            fullHistory.add(Map.of("role", "system", "content", systemPrompt));
            for (ConversationMessage msg : request.getHistory()) {
                fullHistory.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
            response = provider.chatCompletionWithHistory(systemPrompt, fullHistory).join();
        } else {
            response = provider.chatCompletion(systemPrompt, effectiveMessage).join();
        }

        conversationManager.addMessage(sessionId, "assistant", response, modelUsed,
                System.currentTimeMillis() - startTime);

        return AgentResponse.builder()
                .sessionId(sessionId)
                .message(response)
                .taskType(request.getTaskType())
                .status("success")
                .toolCalls(Collections.emptyList())
                .metadata(Map.of("contextSize", conversationManager.getHistory(sessionId).size()))
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(modelUsed)
                .build();
    }

    private String buildDemoResponse(AgentRequest request) {
        String msg = request.getMessage() != null ? request.getMessage().toLowerCase() : "";

        if (msg.contains("分析") || msg.contains("市场") || msg.contains("适合") || msg.contains("出口")) {
            return """
                    📊 [演示模式] 展示架市场分析

                    我可以帮您分析以下维度：
                    1. **市场需求**: 目标市场零售规模和展示架采购趋势
                    2. **竞争格局**: 中国出口商在目标市场的竞争优势分析
                    3. **合规准入**: FSC认证、ISTA包装标准、ROHS油墨要求
                    4. **物流成本**: FOB深圳 vs DDP，Flat Pack运费优化
                    5. **展会推荐**: EuroShop、GlobalShop、POPAI等行业展会

                    💡 请在「展示架市场分析」页面使用专用功能，
                    或配置 DeepSeek API Key 后我将直接为您生成完整报告。
                    """;
        }

        if (msg.contains("翻译") || msg.contains("translate")) {
            return """
                    🌐 [演示模式] 翻译功能

                    我支持以下翻译能力：
                    - 中文 → 英文/日文/韩文/德文/法文
                    - 英文 → 日文/中文/韩文
                    - 电商本地化翻译（保留营销效果）

                    💡 请在「多语言翻译」页面使用专用翻译功能，
                    或配置 API Key 开启 AI 智能翻译。

                    📌 配置方式: 编辑 application-secrets.yml，填写 DEEPSEEK_API_KEY
                    """;
        }

        return String.format("""
                🤖 [演示模式] 杰创展示 AI 助手

                我是JC Display的B2B出口AI助手，可以帮您：
                - ✍️ **产品详情文案** (Alibaba/GlobalSources)
                - ✉️ **英文询盘回复邮件**
                - 🌐 **多语言翻译** (展示架行业术语)
                - 📊 **出口市场分析** (目标国家评估)
                - 🔧 **工具调用** (搜索/翻译/汇率/SEO)

                💡 配置 DeepSeek API Key 后可获得真正的 AI 能力。

                📌 快速配置:
                编辑 application-secrets.yml → 填入 DEEPSEEK_API_KEY → 重启后端
                """, msg);
    }

    private AgentResponse dispatchWithTools(AgentRequest request, String sessionId,
                                            LLMProvider provider, String modelUsed, long startTime) {
        List<Map<String, Object>> toolDefs = toolRegistry.getToolDefinitionsForLLM();

        String systemPrompt = buildAgentSystemPrompt(request, toolDefs);
        String ragAugmentedMsg = ragService.augmentPrompt(request.getMessage());
        String effectiveMsg = ragAugmentedMsg != null ? ragAugmentedMsg : request.getMessage();

        List<AgentResponse.ToolCallRecord> toolCallRecords = new ArrayList<>();
        int maxRounds = aiConfig.getAgent().getMaxConversationRounds();
        String finalResponse = null;

        for (int round = 0; round < maxRounds; round++) {
            String llmResponse;
            if (round == 0) {
                llmResponse = provider.chatCompletionWithTools(systemPrompt, effectiveMsg, toolDefs).join();
            } else {
                List<Map<String, String>> history = conversationManager.getHistoryForLLM(sessionId);
                llmResponse = provider.chatCompletionWithHistory(systemPrompt, history).join();
            }

            String toolCallJson = extractToolCallJson(llmResponse);
            if (toolCallJson == null) {
                finalResponse = llmResponse;
                break;
            }

            try {
                JsonNode toolNode = objectMapper.readTree(toolCallJson);
                String toolName = toolNode.get("name").asText();
                JsonNode argsNode = toolNode.get("arguments");
                Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);

                long toolStart = System.currentTimeMillis();
                Tool tool = toolRegistry.getTool(toolName);
                String toolResult;
                if (tool != null) {
                    toolResult = tool.execute(args).get(aiConfig.getAgent().getToolCallTimeout(), TimeUnit.MILLISECONDS);
                } else {
                    toolResult = "工具 " + toolName + " 未注册";
                }

                toolCallRecords.add(AgentResponse.ToolCallRecord.builder()
                        .toolName(toolName)
                        .input(args != null ? args.toString() : "{}")
                        .output(toolResult)
                        .status("success")
                        .durationMs(System.currentTimeMillis() - toolStart)
                        .build());

                conversationManager.addToolMessage(sessionId, "assistant", llmResponse, toolName, toolResult);
                log.info("工具 {} 执行完成 (round={}): {}", toolName, round,
                        toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);

            } catch (Exception e) {
                log.error("工具调用失败 (round {})", round, e);
                toolCallRecords.add(AgentResponse.ToolCallRecord.builder()
                        .toolName("error")
                        .input(toolCallJson)
                        .output(e.getMessage())
                        .status("failed")
                        .durationMs(0)
                        .build());
                finalResponse = "工具调用出错: " + e.getMessage();
                break;
            }
        }

        if (finalResponse == null) {
            finalResponse = "已达到最大工具调用轮次 (" + maxRounds + ")，请尝试简化问题。";
        }

        String cleanResponse = stripToolCallJson(finalResponse);
        conversationManager.addMessage(sessionId, "assistant", cleanResponse, modelUsed,
                System.currentTimeMillis() - startTime);

        return AgentResponse.builder()
                .sessionId(sessionId)
                .message(cleanResponse)
                .taskType(request.getTaskType())
                .status("success")
                .toolCalls(toolCallRecords)
                .metadata(Map.of(
                        "contextSize", conversationManager.getHistory(sessionId).size(),
                        "toolsEnabled", true,
                        "toolsAvailable", toolRegistry.getAllTools().stream().map(Tool::getName).toList(),
                        "toolCallRounds", toolCallRecords.size()
                ))
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(modelUsed)
                .build();
    }

    private String extractToolCallJson(String llmResponse) {
        if (llmResponse == null) return null;
        int start = llmResponse.indexOf("{");
        int end = llmResponse.lastIndexOf("}");
        if (start >= 0 && end > start) {
            String candidate = llmResponse.substring(start, end + 1);
            if (candidate.contains("\"name\"") && candidate.contains("\"arguments\"")) {
                return candidate;
            }
        }
        return null;
    }

    private String stripToolCallJson(String text) {
        if (text == null) return null;
        int braceIdx = text.indexOf("{\"name\"");
        if (braceIdx > 0) {
            String before = text.substring(0, braceIdx).trim();
            return before.isEmpty() ? text : before;
        }
        return text;
    }

    private String buildSystemPrompt(AgentRequest request) {
        Map<String, String> vars = new HashMap<>();
        vars.put("targetCountry", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("targetCountry", "US")
                : "US");
        vars.put("language", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("language", "English")
                : "English");
        return promptManager.renderTemplate("agent-system", vars);
    }

    private String buildAgentSystemPrompt(AgentRequest request, List<Map<String, Object>> toolDefs) {
        Map<String, String> vars = new HashMap<>();
        vars.put("targetCountry", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("targetCountry", "US")
                : "US");
        vars.put("language", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("language", "English")
                : "English");

        StringBuilder toolsDesc = new StringBuilder();
        for (Map<String, Object> def : toolDefs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> func = (Map<String, Object>) def.get("function");
            if (func != null) {
                toolsDesc.append("- **").append(func.get("name")).append("**: ")
                        .append(func.get("description")).append("\n");
            }
        }
        vars.put("toolDefinitions", toolsDesc.toString());
        return promptManager.renderTemplate("agent-system", vars);
    }

    private String ensureSession(String sessionId, String operationType) {
        if (sessionId != null && conversationManager.sessionExists(sessionId)) {
            return sessionId;
        }
        if (sessionId != null) {
            return conversationManager.createSession(sessionId, null, operationType);
        }
        return conversationManager.createSession(null, operationType);
    }

    /**
     * Map taskType to normalized operationType for dashboard grouping.
     * Each distinct Agent gets its own type label with a dedicated color.
     */
    private String toOperationType(String taskType) {
        if (taskType == null) return "chat";
        return switch (taskType) {
            case "chat"             -> "chat";
            case "translation"      -> "translate";
            case "inquiry_scoring"  -> "inquiry";
            case "analysis"         -> "analysis";
            case "copywriting"      -> "copywriting";
            default                 -> "chat";
        };
    }
}
