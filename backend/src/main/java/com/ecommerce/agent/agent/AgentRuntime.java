package com.ecommerce.agent.agent;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.llm.LLMProvider;
import com.ecommerce.agent.llm.MultiModelOrchestrator;
import com.ecommerce.agent.llm.PromptTemplateManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.model.v2.AgentTask;
import com.ecommerce.agent.rag.RAGService;
import com.ecommerce.agent.repository.AgentTaskRepository;
import com.ecommerce.agent.service.SessionTitleService;
import com.ecommerce.agent.service.v2.MemoryService;
import com.ecommerce.agent.tool.ToolRouter;
import com.ecommerce.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

/**
 * v2 Agent Runtime — 核心执行引擎
 *
 * 执行循环 (while !done):
 * ┌─────────────────────────────────────┐
 * │ 1. buildContext(user, task, memory) │
 * │ 2. llm.chat(context, tools)         │
 * │ 3. if hasToolCall() → execute tools │
 * │    else → return finalAnswer        │
 * └─────────────────────────────────────┘
 *
 * 相比 v1 AgentDispatcher 的升级:
 * - 显式 while(!done) 循环, 条件终止判断
 * - Memory 系统集成 (短期+长期)
 * - Task 上下文注入
 * - ToolRouter 统一调度
 * - 执行链路可追溯
 */
@Slf4j
@Service
public class AgentRuntime {

    private final MultiModelOrchestrator orchestrator;
    private final PromptTemplateManager promptManager;
    private final ConversationManager conversationManager;
    private final ToolRegistry toolRegistry;
    private final ToolRouter toolRouter;
    private final AIConfig aiConfig;
    private final RAGService ragService;
    private final MemoryService memoryService;
    private final SessionTitleService titleService;
    private final AgentTaskRepository taskRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentRuntime(MultiModelOrchestrator orchestrator,
                        PromptTemplateManager promptManager,
                        ConversationManager conversationManager,
                        ToolRegistry toolRegistry,
                        ToolRouter toolRouter,
                        AIConfig aiConfig,
                        RAGService ragService,
                        MemoryService memoryService,
                        SessionTitleService titleService,
                        AgentTaskRepository taskRepo) {
        this.orchestrator = orchestrator;
        this.promptManager = promptManager;
        this.conversationManager = conversationManager;
        this.toolRegistry = toolRegistry;
        this.toolRouter = toolRouter;
        this.aiConfig = aiConfig;
        this.ragService = ragService;
        this.memoryService = memoryService;
        this.titleService = titleService;
        this.taskRepo = taskRepo;
    }

    // 临时图片存储 (sessionId → base64 images)
    private final Map<String, List<String>> sessionImages = new ConcurrentHashMap<>();

    /**
     * v2 主执行入口 — Agent Runtime 核心循环
     */
    public AgentResponse execute(AgentRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 准备执行上下文
        String requestUserId = request.getUserId();
        String requestUsername = request.getUsername();
        String sessionId = ensureSession(request.getSessionId(), request.getTaskType(), requestUserId, requestUsername);
        boolean isNewSession = request.getSessionId() == null
                || !conversationManager.sessionExists(request.getSessionId());

        // 1a. 提取图片（data URI 列表）
        List<String> images = extractImages(request);
        if (!images.isEmpty()) {
            sessionImages.put(sessionId, images);
        }

        // 1b. 用户消息 — 如果有图片则用原始消息（LLM 同时看到文字+图片）
        String userMessage = request.getMessage() != null && !request.getMessage().isBlank()
                ? request.getMessage()
                : (images.isEmpty() ? "你好" : "");
        conversationManager.addMessage(sessionId, "user",
                images.isEmpty() ? userMessage : (userMessage + " [图片x" + images.size() + "]"),
                null, 0L, request.getTaskType(), requestUserId, requestUsername);

        if (isNewSession) {
            titleService.autoTitle(sessionId, images.isEmpty() ? userMessage : userMessage);
        }

        // 2. 获取 LLM Provider
        LLMProvider provider = orchestrator.getProvider();
        String modelUsed = provider.getDefaultModel();

        AgentResponse directResponse = tryHandleKnowledgeStatusIntent(request, sessionId, userMessage, modelUsed, startTime);
        if (directResponse != null) {
            return directResponse;
        }

        // 3. 构建增强系统 Prompt (Memory + RAG + Task Context)
        EnhancedSystemPrompt enhancedPrompt = buildEnhancedSystemPrompt(request, sessionId);
        String systemPrompt = enhancedPrompt.prompt();
        RAGService.RAGContext ragContext = enhancedPrompt.ragContext();

        // 4. 开始 Agent 循环
        ExecutionState state = new ExecutionState(sessionId, provider, modelUsed, startTime);
        List<AgentResponse.ToolCallRecord> toolCallRecords = new ArrayList<>();

        int maxRounds = aiConfig.getAgent().getMaxConversationRounds();
        String currentMessage = userMessage;

        // ═══ Agent 核心循环 ═══
        for (int round = 0; round < maxRounds; round++) {
            log.info("Agent循环 round={}/{} session={}", round + 1, maxRounds,
                    sessionId.substring(0, Math.min(8, sessionId.length())));

            // Step A: 获取工具定义
            List<Map<String, Object>> toolDefs = enableTools(request)
                    ? toolRegistry.getToolDefinitionsForLLM() : List.of();

            // Step B: 调用 LLM — 首轮有图片则多模态
            String llmResponse;
            try {
                if (round == 0 && !images.isEmpty()) {
                    // 多模态模式：LLM 直接看到图片 + 文字（DeepSeek 不支持则自动回退 OpenAI）
                    llmResponse = orchestrator.chatWithToolsAndImages(
                            systemPrompt, currentMessage, toolDefs, images)
                            .get(aiConfig.getAgent().getToolCallTimeout(), TimeUnit.MILLISECONDS);
                } else if (round == 0) {
                    llmResponse = provider.chatCompletionWithTools(systemPrompt, currentMessage, toolDefs)
                            .get(aiConfig.getAgent().getToolCallTimeout(), TimeUnit.MILLISECONDS);
                } else {
                    List<Map<String, String>> history = conversationManager.getHistoryForLLM(sessionId);
                    llmResponse = provider.chatCompletionWithToolsAndHistory(systemPrompt, history, toolDefs)
                            .get(aiConfig.getAgent().getToolCallTimeout(), TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("LLM调用失败 round={}", round, e);
                state.done = true;
                String errMsg = e.getMessage();
                if (errMsg == null || errMsg.isBlank()) {
                    errMsg = e.getClass().getSimpleName()
                            + (e.getCause() != null ? ": " + e.getCause().getMessage() : "");
                    if (errMsg.endsWith(": null")) errMsg = e.getClass().getSimpleName();
                }
                state.finalAnswer = "抱歉，AI服务暂时不可用: " + errMsg;
                break;
            }

            // Step C: 判断是否有工具调用
            ToolRouter.ToolCallResult toolResult = toolRouter.routeWithContext(llmResponse,
                    Map.of("session_id", sessionId));

            if (toolResult.success() && toolResult.toolName() != null) {
                // ── 有工具调用 ──

                log.info("工具调用: {} round={}", toolResult.toolName(), round);

                // 记录工具调用
                toolCallRecords.add(AgentResponse.ToolCallRecord.builder()
                        .toolName(toolResult.toolName())
                        .input(toolResult.input() != null ? toolResult.input().toString() : "{}")
                        .output(toolResult.output())
                        .status("success")
                        .durationMs(toolResult.durationMs())
                        .build());

                // 判断是否为"终点型"工具（多模态生成类），直接返回结果给用户
                boolean isTerminalTool = toolRegistry.getTool(toolResult.toolName()) != null
                        && "MULTIMODAL".equals(toolRegistry.getTool(toolResult.toolName()).getCategory());

                if (isTerminalTool && toolResult.output() != null && !toolResult.output().isBlank()) {
                    // 图片生成/识别等：工具输出直接作为最终答案，不让 LLM 再处理一遍
                    state.finalAnswer = toolResult.output();
                    state.done = true;
                    state.answerSaved = true;
                    conversationManager.addMessage(sessionId, "assistant", state.finalAnswer,
                            modelUsed, System.currentTimeMillis() - startTime,
                            request.getTaskType(), requestUserId, requestUsername);
                } else {
                    // 普通工具：将结果注入对话继续循环
                    conversationManager.addToolMessage(sessionId, "assistant",
                            "调用工具 " + toolResult.toolName(),
                            toolResult.toolName(), toolResult.output(),
                            requestUserId, requestUsername);

                    if (toolResult.output() != null && !toolResult.output().isBlank()) {
                        currentMessage = "工具 " + toolResult.toolName() + " 返回结果:\n"
                                + (toolResult.output().length() > 3000
                                ? toolResult.output().substring(0, 3000) + "..."
                                : toolResult.output())
                                + "\n\n请基于以上结果继续处理。";
                    }
                }

                state.roundsExecuted = round + 1;

            } else if (toolResult.toolName() != null && !toolResult.success()) {
                // ── 工具调用失败 ──

                log.warn("工具调用失败: {} round={} error={}",
                        toolResult.toolName(), round, toolResult.error());

                toolCallRecords.add(AgentResponse.ToolCallRecord.builder()
                        .toolName(toolResult.toolName())
                        .input(toolResult.input() != null ? toolResult.input().toString() : "{}")
                        .output(toolResult.error())
                        .status("failed")
                        .durationMs(toolResult.durationMs())
                        .build());

                if ("wechat_control".equals(toolResult.toolName())) {
                    state.finalAnswer = formatWeChatFailure(toolResult.error());
                    state.done = true;
                    state.roundsExecuted = round + 1;
                    break;
                }

                // 告诉 LLM 工具失败了, 让它自行处理
                currentMessage = "工具 " + toolResult.toolName() + " 执行失败: "
                        + toolResult.error() + "。请尝试其他方式完成用户的请求。";
                state.roundsExecuted = round + 1;

            } else {
                // ── 无工具调用: 任务完成 ──

                // 清理 LLM 响应中的工具调用 JSON
                state.finalAnswer = cleanResponse(llmResponse);
                state.done = true;
                break;
            }
        }
        // ═══ 循环结束 ═══

        // 后处理
        if (!state.done && state.finalAnswer == null) {
            state.finalAnswer = "已达到最大执行轮次 (" + maxRounds
                    + ")，任务可能过于复杂。请尝试拆分为更小的步骤。";
        }

        // 保存最终回答到对话历史（终端工具已保存则跳过）
        if (!state.answerSaved) {
            conversationManager.addMessage(sessionId, "assistant", state.finalAnswer,
                    modelUsed, System.currentTimeMillis() - startTime,
                    request.getTaskType(), requestUserId, requestUsername);
        }

        // 从对话中学习 (新记忆)
        if (enableTools(request) && !toolCallRecords.isEmpty()) {
            try {
                memoryService.learnFromConversation(
                        "user", sessionId,
                        conversationManager.getHistory(sessionId));
            } catch (Exception e) {
                log.debug("记忆学习失败(非致命): {}", e.getMessage());
            }
        }

        // 构建响应
        AgentResponse response = AgentResponse.builder()
                .sessionId(sessionId)
                .message(state.finalAnswer)
                .taskType(request.getTaskType())
                .status("success")
                .toolCalls(toolCallRecords)
                .metadata(Map.of(
                        "contextSize", conversationManager.getHistory(sessionId).size(),
                        "toolsEnabled", enableTools(request),
                        "toolsAvailable", toolRegistry.getAllTools().stream()
                                .map(t -> t.getName()).toList(),
                        "toolCallRounds", toolCallRecords.size(),
                        "totalRounds", state.roundsExecuted,
                        "memoryEnabled", true,
                        "ragUsed", ragContext.hasContext(),
                        "ragCitations", ragContext.citations(),
                        "runtimeVersion", "v2"
                ))
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(modelUsed)
                .build();

        log.info("Agent执行完成: session={}, rounds={}, tools={}, time={}ms",
                sessionId.substring(0, 8), state.roundsExecuted,
                toolCallRecords.size(), response.getProcessingTimeMs());

        return response;
    }

    /**
     * 按任务定义执行 Agent (Task System 调用)
     */
    public AgentResponse executeTask(AgentTask task) {
        AgentRequest request = AgentRequest.builder()
                .sessionId(task.getSessionId())
                .message(task.getDescription() != null ? task.getDescription() : task.getName())
                .taskType(task.getType())
                .enableTools(task.getToolList() != null && !task.getToolList().isBlank())
                .parameters(parseTaskContext(task.getContext()))
                .build();

        // 注入工作流定义
        if (task.getWorkflowDefinition() != null) {
            request.setParameters(mergeParams(request.getParameters(),
                    Map.of("_workflow", task.getWorkflowDefinition())));
        }

        return execute(request);
    }

    // ═══════════════════════════════════════════════════════════════
    // Private Helpers
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<String> extractImages(AgentRequest request) {
        if (request.getParameters() == null) return List.of();
        Object imgs = request.getParameters().get("_images");
        if (imgs instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof String s && s.startsWith("data:")) {
            return ((List<String>) imgs);
        }
        return List.of();
    }

    /** 获取会话的临时图片 (供 Tool 使用) */
    public List<String> getSessionImages(String sessionId) {
        return sessionImages.getOrDefault(sessionId, List.of());
    }

    private AgentResponse tryHandleKnowledgeStatusIntent(AgentRequest request,
                                                         String sessionId,
                                                         String userMessage,
                                                         String modelUsed,
                                                         long startTime) {
        if (!isKnowledgeStatusIntent(userMessage) || !toolRegistry.hasTool("knowledge_base_status")) {
            return null;
        }

        ToolRouter.ToolCallResult toolResult = toolRouter.execute(
                "knowledge_base_status",
                Map.of("include_topics", false),
                Map.of("session_id", sessionId)
        );

        AgentResponse.ToolCallRecord record = AgentResponse.ToolCallRecord.builder()
                .toolName("knowledge_base_status")
                .input("{include_topics=false}")
                .output(toolResult.output())
                .status(toolResult.success() ? "success" : "failed")
                .durationMs(toolResult.durationMs())
                .build();

        String answer = toolResult.success()
                ? formatKnowledgeStatusAnswer(toolResult.output())
                : "查询 RAG 产品库状态失败: " + toolResult.error();

        long durationMs = System.currentTimeMillis() - startTime;
        conversationManager.addMessage(sessionId, "assistant", answer, modelUsed, durationMs,
                request.getTaskType(), request.getUserId(), request.getUsername());

        return AgentResponse.builder()
                .sessionId(sessionId)
                .message(answer)
                .taskType(request.getTaskType())
                .status(toolResult.success() ? "success" : "failed")
                .toolCalls(List.of(record))
                .metadata(Map.of(
                        "contextSize", conversationManager.getHistory(sessionId).size(),
                        "toolsEnabled", true,
                        "toolsAvailable", toolRegistry.getAllTools().stream()
                                .map(t -> t.getName()).toList(),
                        "toolCallRounds", 1,
                        "totalRounds", 1,
                        "memoryEnabled", true,
                        "ragUsed", false,
                        "ragCitations", List.of(),
                        "runtimeVersion", "v2",
                        "directKnowledgeStatusRoute", true
                ))
                .processingTimeMs(durationMs)
                .modelUsed(modelUsed)
                .build();
    }

    private boolean isKnowledgeStatusIntent(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s　，。！？?；;：:,、\"'“”‘’()（）\\[\\]{}]+", "");

        boolean countIntent = containsAny(text, "多少", "几个", "几款", "数量", "总数", "统计", "count", "howmany", "numberof");
        boolean productLibrary = containsAny(text, "rag产品库", "rag商品库", "产品库", "商品库", "本公司产品", "公司产品",
                "productlibrary", "productcatalog", "productcount", "ragproducts");
        boolean knowledgeDocuments = containsAny(text, "知识库文档", "rag文档", "文档数量", "资料数量",
                "knowledgedocument", "documentscount");
        boolean vectorOrIndex = containsAny(text, "向量化", "向量索引", "索引状态", "embedding", "vectorindex",
                "indexed", "indexstatus");
        boolean ragStatus = text.contains("rag") && containsAny(text, "状态", "status", "索引", "向量", "数量", "count");

        return (countIntent && productLibrary) || knowledgeDocuments || vectorOrIndex || ragStatus;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String formatKnowledgeStatusAnswer(String output) {
        try {
            JsonNode root = objectMapper.readTree(output);
            long productCount = root.path("rag_product_count").asLong();
            long enabledProductCount = root.path("enabled_product_count").asLong();
            long documentCount = root.path("knowledge_document_count").asLong();
            boolean vectorEnabled = root.path("product_vector_index_enabled").asBoolean();
            boolean indexPersisted = root.path("embedding_index_persisted").asBoolean();
            boolean manifestPersisted = root.path("embedding_manifest_persisted").asBoolean();
            String scope = root.path("product_embedding_scope").asText("unknown");

            return "当前 RAG 产品库中查询到 " + productCount + " 个产品，其中启用产品 "
                    + enabledProductCount + " 个。知识库文档为 " + documentCount + " 个。"
                    + "产品向量索引" + (vectorEnabled ? "已启用" : "未启用")
                    + "，向量化范围为 " + ("all".equals(scope) ? "全部产品" : "限制数量")
                    + "；索引文件" + (indexPersisted ? "已持久化" : "尚未持久化")
                    + "，增量清单" + (manifestPersisted ? "已持久化" : "尚未持久化")
                    + "。这个结果来自 RAG 产品库状态工具，不是会话数量。";
        } catch (Exception e) {
            log.warn("格式化知识库状态失败: {}", e.getMessage());
            return output;
        }
    }

    private String formatWeChatFailure(String output) {
        try {
            JsonNode root = objectMapper.readTree(output);
            String toUserId = root.path("to_user_id").asText("");
            JsonNode raw = root.path("raw_response");
            String ret = raw.has("ret") ? raw.path("ret").asText() : "";
            String errcode = raw.has("errcode") ? raw.path("errcode").asText() : "";
            String error = root.path("error").asText("微信 API 返回失败");

            StringBuilder sb = new StringBuilder("微信 API 返回失败，消息没有发送成功。");
            if (!toUserId.isBlank()) sb.append("\n\n目标: ").append(toUserId);
            if (!ret.isBlank()) sb.append("\nret: ").append(ret);
            if (!errcode.isBlank()) sb.append("\nerrcode: ").append(errcode);
            sb.append("\n原因: ").append(error);
            sb.append("\n\n当前 JC claw 只能发送给 iLink API 允许的目标；如果 filehelper 返回 ret=-3，说明这个 Bot 通道没有获得向文件传输助手发送消息的权限或该目标不被 iLink Bot 支持。");
            return sb.toString();
        } catch (Exception e) {
            return "微信 API 返回失败，消息没有发送成功。\n\n" + output;
        }
    }

    private EnhancedSystemPrompt buildEnhancedSystemPrompt(AgentRequest request, String sessionId) {
        Map<String, String> vars = new HashMap<>();
        vars.put("targetCountry", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("targetCountry", "US")
                : "US");
        vars.put("language", request.getParameters() != null
                ? (String) request.getParameters().getOrDefault("language", "English")
                : "English");

        // v2: 注入工具描述
        vars.put("toolDefinitions", toolRouter.buildToolDescriptions());

        String basePrompt = promptManager.renderTemplate("agent-system", vars);

        // v2: 注入 Memory 上下文
        StringBuilder enhanced = new StringBuilder(basePrompt);
        enhanced.append("\n\n");

        String memoryContext = memoryService.buildMemoryContext("user",
                request.getMessage(), 5);
        if (memoryContext != null) {
            enhanced.append(memoryContext);
        }

        enhanced.append("""

## 工具调用约束
当用户询问 RAG 产品库数量、公司产品库中有多少产品、知识库文档数量、产品是否已向量化、索引状态时，必须调用 knowledge_base_status 工具获取真实数据。不要使用 sessions、对话记录数量或历史会话统计来回答产品库数量。
当用户询问本公司产品、产品推荐、产品价格、材料、SKU、分类、产品详情或产品网址时，必须优先调用 product_catalog_search；回答中每个被推荐或提及的产品都必须附上 Product URL/产品链接。没有链接时明确说明产品库暂未记录链接，不要省略这个字段。
当用户在网页端或 JC claw 中要求“给某个微信用户/客户/联系人发消息、通知、跟进、转发内容”时，必须调用 wechat_control。若目标不明确，先调用 list_users 或用 to_user_keyword 匹配，不要假装已经发送。
JC claw 不能读取完整微信通讯录。微信发送能力仅覆盖“文件传输助手(filehelper)”和已与 JC claw 产生过会话的活跃用户。用户要求发给文件传输助手时，直接调用 wechat_control send_message，目标用 to_user_id=filehelper 或 to_user_keyword=文件传输助手。
如果 wechat_control 返回 success=false、business_error 或 iLink ret/errcode 非 0，必须明确告诉用户“微信 API 返回失败，消息未发送成功”，并附上 ret/errcode；不要猜测已经发送，也不要编造联系人权限。
""");

        int ragMaxResults = 6;
        if (request.getParameters() != null && request.getParameters().get("ragTopK") != null) {
            try {
                ragMaxResults = Math.max(1, Math.min(10,
                        Integer.parseInt(request.getParameters().get("ragTopK").toString())));
            } catch (NumberFormatException ignored) {}
        }
        RAGService.RAGContext ragContext = ragService.buildContext(request.getMessage(), ragMaxResults);
        if (ragContext.hasContext()) {
            enhanced.append("""

## 可用知识库上下文
以下内容来自企业知识库、产品库或用户上传文档。回答涉及公司、产品、材料、报价、交付、合规、客户询盘时，必须优先参考这些内容；如果内容不足，请明确说明缺口，不要编造。
如果上下文中出现产品链接/Product URL/链接字段，最终回答必须保留这些网址，尤其是产品推荐、报价、材料说明和产品对比场景。
""");
            enhanced.append(ragContext.context());
            enhanced.append("\n\n回答时请自然引用上述事实，不要泄露内部 chunk 格式。\n");
        }

        // v2: 注入客户上下文
        if (request.getParameters() != null
                && request.getParameters().containsKey("customerId")) {
            String customerCtx = memoryService.buildCustomerContext(
                    request.getParameters().get("customerId").toString());
            if (customerCtx != null) {
                enhanced.append("\n").append(customerCtx);
            }
        }

        return new EnhancedSystemPrompt(enhanced.toString(), ragContext);
    }

    private record EnhancedSystemPrompt(String prompt, RAGService.RAGContext ragContext) {}

    private String ensureSession(String sessionId, String operationType, String userId, String username) {
        if (sessionId != null && conversationManager.sessionExists(sessionId)) {
            return sessionId;
        }
        if (sessionId != null) {
            return conversationManager.createSession(sessionId, null, operationType, userId, username);
        }
        return conversationManager.createSession(null, operationType, userId, username);
    }

    private boolean enableTools(AgentRequest request) {
        return request.isEnableTools() && aiConfig.isDeepSeekKeyConfigured();
    }

    private String cleanResponse(String llmResponse) {
        if (llmResponse == null) return null;
        int braceIdx = llmResponse.indexOf("{\"name\"");
        if (braceIdx > 0) {
            String before = llmResponse.substring(0, braceIdx).trim();
            return before.isEmpty() ? llmResponse : before;
        }
        // 也清理 markdown code block
        String cleaned = llmResponse.replaceAll("```json[\\s\\S]*?```", "");
        return cleaned.isBlank() ? llmResponse : cleaned.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTaskContext(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(contextJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> mergeParams(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> merged = new LinkedHashMap<>(a != null ? a : Map.of());
        merged.putAll(b);
        return merged;
    }

    /**
     * 执行状态跟踪
     */
    private static class ExecutionState {
        final String sessionId;
        final LLMProvider provider;
        final String modelUsed;
        final long startTime;
        boolean done = false;
        String finalAnswer;
        int roundsExecuted = 0;
        boolean answerSaved = false;  // 防止 MULTIMODAL 路径重复存入对话历史

        ExecutionState(String sessionId, LLMProvider provider, String modelUsed, long startTime) {
            this.sessionId = sessionId;
            this.provider = provider;
            this.modelUsed = modelUsed;
            this.startTime = startTime;
        }
    }
}
