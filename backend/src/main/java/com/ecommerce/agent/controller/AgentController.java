package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.llm.MultiModelOrchestrator;
import com.ecommerce.agent.llm.PromptTemplateManager;
import com.ecommerce.agent.model.*;
import com.ecommerce.agent.rag.KnowledgeBaseLoader;
import com.ecommerce.agent.rag.RAGService;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.ecommerce.agent.repository.ConversationSessionRepository;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import com.ecommerce.agent.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentRuntime agentRuntime;
    private final ConversationManager conversationManager;
    private final MultiModelOrchestrator orchestrator;
    private final AIConfig aiConfig;
    private final PromptTemplateManager promptTemplateManager;
    private final RAGService ragService;
    private final KnowledgeDocumentRepository knowledgeDocRepo;
    private final ProductRepository productRepo;
    private final ConversationRecordRepository recordRepo;
    private final ConversationSessionRepository sessionRepo;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    public AgentController(AgentRuntime agentRuntime,
                           ConversationManager conversationManager,
                           MultiModelOrchestrator orchestrator,
                           AIConfig aiConfig,
                           PromptTemplateManager promptTemplateManager,
                           RAGService ragService,
                           KnowledgeDocumentRepository knowledgeDocRepo,
                           ProductRepository productRepo,
                        ConversationRecordRepository recordRepo,
                        ConversationSessionRepository sessionRepo,
                        KnowledgeBaseLoader knowledgeBaseLoader) {
        this.agentRuntime = agentRuntime;
        this.conversationManager = conversationManager;
        this.orchestrator = orchestrator;
        this.aiConfig = aiConfig;
        this.promptTemplateManager = promptTemplateManager;
        this.ragService = ragService;
        this.knowledgeDocRepo = knowledgeDocRepo;
        this.productRepo = productRepo;
        this.recordRepo = recordRepo;
        this.sessionRepo = sessionRepo;
        this.knowledgeBaseLoader = knowledgeBaseLoader;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                                    Authentication authentication) {
        String sessionId = (String) body.getOrDefault("sessionId", null);
        String message = (String) body.getOrDefault("message", "");
        boolean enableTools = Boolean.TRUE.equals(body.get("enableTools"));

        AgentRequest request = AgentRequest.builder()
            .sessionId(sessionId)
            .taskType("chat")
            .message(message)
            .enableTools(enableTools)
            .userId(currentUsername(authentication))
            .username(currentUsername(authentication))
            .build();

        AgentResponse response = agentRuntime.execute(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", response.getSessionId());
        result.put("message", response.getMessage());
        result.put("modelUsed", response.getModelUsed());
        result.put("processingTimeMs", response.getProcessingTimeMs());

        List<Map<String, Object>> tcList = new ArrayList<>();
        if (response.getToolCalls() != null) {
            for (var tc : response.getToolCalls()) {
                Map<String, Object> tcm = new LinkedHashMap<>();
                tcm.put("toolName", tc.getToolName());
                tcm.put("output", tc.getOutput());
                tcm.put("durationMs", tc.getDurationMs());
                tcList.add(tcm);
            }
        }
        result.put("toolCalls", tcList);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/chat/tools")
    public ResponseEntity<Map<String, Object>> chatWithTools(@RequestBody Map<String, Object> body,
                                                             Authentication authentication) {
        body.put("enableTools", true);
        return chat(body, authentication);
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(@RequestParam(required = false) String type) {
        List<Map<String, Object>> sessions;
        if (type != null && !type.isEmpty()) {
            sessions = conversationManager.getSessionList(type);
        } else {
            sessions = conversationManager.getSessionList();
        }

        List<Map<String, Object>> enriched = sessions.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>(s);
            String sid = (String) s.get("sessionId");
            String opType = (String) s.get("operationType");
            String title = (String) s.get("title");

            // 微信会话统一显示"微信对话"，避免 ID 乱码
            if (sid != null && sid.startsWith("wx_")) {
                title = "微信对话";
                m.put("title", title);
                opType = "wechat";
            }

            if (opType == null || opType.isBlank()) {
                opType = inferType(title);
            }
            m.put("type", opType);
            m.put("modelUsed", "deepseek-chat");

            List<ConversationRecord> records = conversationManager.getDBHistory(sid);
            if (!records.isEmpty()) {
                ConversationRecord last = records.get(records.size() - 1);
                m.put("modelUsed", last.getModelUsed() != null ? last.getModelUsed() : "deepseek-chat");
            }
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("sessions", enriched));
    }

    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String sessionId) {
        List<ConversationMessage> history = conversationManager.getHistory(sessionId);
        return ResponseEntity.ok(Map.of("records", history.stream().map(msg -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("toolName", msg.getToolName());
            m.put("toolResult", msg.getToolResult());
            m.put("timestamp", msg.getTimestamp());
            return m;
        }).collect(Collectors.toList())));
    }

    @GetMapping("/session/{sessionId}/history/db")
    public ResponseEntity<Map<String, Object>> getDBHistory(@PathVariable String sessionId) {
        List<ConversationRecord> records = conversationManager.getDBHistory(sessionId);
        return ResponseEntity.ok(Map.of("records", records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r.getRole());
            m.put("content", r.getContent());
            m.put("toolName", r.getToolName());
            m.put("toolResult", r.getToolResult());
            m.put("processingTimeMs", r.getProcessingTimeMs());
            m.put("modelUsed", r.getModelUsed());
            m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList())));
    }

    @PutMapping("/session/{sessionId}/title")
    public ResponseEntity<Map<String, Object>> updateTitle(@PathVariable String sessionId,
                                                            @RequestBody Map<String, String> body) {
        conversationManager.updateSessionTitle(sessionId, body.get("title"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/session/{sessionId}/auto-title")
    public ResponseEntity<Map<String, Object>> autoTitle(@PathVariable String sessionId) {
        List<ConversationRecord> records = conversationManager.getDBHistory(sessionId);
        if (records.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "title", "空对话"));
        }

        // Build conversation summary from first few messages
        StringBuilder sb = new StringBuilder();
        int max = Math.min(records.size(), 6);
        for (int i = 0; i < max; i++) {
            ConversationRecord r = records.get(i);
            String content = r.getContent();
            if (content != null) {
                int limit = Math.min(content.length(), 100);
                sb.append(r.getRole()).append(": ").append(content, 0, limit).append("\n");
            }
        }

        if (!aiConfig.isDeepSeekKeyConfigured()) {
            String fallback = sb.length() > 30 ? sb.substring(0, 30).replace('\n', ' ') : sb.toString().replace('\n', ' ');
            conversationManager.updateSessionTitle(sessionId, fallback);
            return ResponseEntity.ok(Map.of("success", true, "title", fallback, "note", "AI未配置，使用摘要"));
        }

        try {
            String systemPrompt = """
                    你是对话标题生成器。根据对话内容生成简短标题。
                    要求：10个汉字或20个英文字符以内，只返回标题，不要任何引号、标点或解释。
                    如果是翻译任务用"翻译: "开头，分析任务用"分析: "开头，文案任务用"文案: "开头。
                    """;
            String title = orchestrator.reasoning(systemPrompt,
                    "为以下对话生成标题:\n" + sb.toString()).get();
            if (title != null && !title.isBlank()) {
                title = title.trim().replaceAll("^[\"']|[\"']$", "");
                if (title.length() > 30) title = title.substring(0, 30);
                conversationManager.updateSessionTitle(sessionId, title);
                return ResponseEntity.ok(Map.of("success", true, "title", title));
            }
        } catch (Exception e) {
            log.warn("AI auto-title failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("success", false, "title", "自动命名失败"));
    }

    @PostMapping("/session/{sessionId}/clear")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        conversationManager.clearSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/knowledge/search")
    public ResponseEntity<Map<String, Object>> searchKnowledge(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int maxResults = body.containsKey("maxResults") ? ((Number) body.get("maxResults")).intValue() : 5;
        RAGService.RAGContext ragContext = ragService.buildContext(query, Math.max(1, Math.min(10, maxResults)));
        List<Map<String, Object>> results = ragContext.citations();
        return ResponseEntity.ok(Map.of(
            "query", query,
            "results", results,
            "context", ragContext.context() != null ? ragContext.context() : "",
            "totalFound", results.size(),
            "ragUsed", ragContext.hasContext()
        ));
    }

    @GetMapping("/knowledge/status")
    public ResponseEntity<Map<String, Object>> knowledgeStatus() {
        boolean available = ragService.isAvailable();
        long docCount = knowledgeDocRepo.count();
        long productCount = productRepo.count();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", available);
        status.put("storeType", "MySQL（持久化存储）");
        status.put("knowledgeDocumentCount", docCount);
        status.put("productCount", productCount);
        status.put("dataSource", "MySQL → knowledge_documents + products 表");
        status.put("autoInject", aiConfig.getRag().isAugmentPrompt());
        status.put("retrievalMode", aiConfig.getRag().isIndexProducts()
                ? "知识文档 + 产品库向量检索，中文查询扩展，中英关键词兜底"
                : "知识文档向量检索 + MySQL 关键词兜底");
        status.put("productVectorIndexEnabled", aiConfig.getRag().isIndexProducts());
        status.put("maxProductEmbeddings", aiConfig.getRag().getMaxProductEmbeddings());
        status.put("productEmbeddingScope", aiConfig.getRag().getMaxProductEmbeddings() == 0 ? "all" : "limited");
        status.put("topics", knowledgeDocRepo.findByEnabledTrueOrderByTitleAsc()
                .stream().map(KnowledgeDocument::getTitle).collect(Collectors.toList()));
        return ResponseEntity.ok(status);
    }

    /**
     * 查看所有 RAG 知识库文档内容（从 MySQL）
     */
    @GetMapping("/knowledge/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        List<KnowledgeDocument> docs = knowledgeDocRepo.findByEnabledTrueOrderByTitleAsc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeDocument kd : docs) {
            result.add(Map.of(
                "id", kd.getId(),
                "title", kd.getTitle(),
                "category", kd.getCategory() != null ? kd.getCategory() : "",
                "content", kd.getContent(),
                "length", kd.getContent().length(),
                "enabled", kd.isEnabled(),
                "createdAt", kd.getCreatedAt() != null ? kd.getCreatedAt().toString() : "",
                "updatedAt", kd.getUpdatedAt() != null ? kd.getUpdatedAt().toString() : ""
            ));
        }
        return ResponseEntity.ok(Map.of(
            "total", result.size(),
            "source", "MySQL",
            "documents", result
        ));
    }

    /**
     * 查询 MySQL 中的产品列表（分页）
     */
    @GetMapping("/knowledge/products")
    public ResponseEntity<Map<String, Object>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = productRepo.findAll(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("createdAt").descending()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (com.ecommerce.agent.model.Product p : pageResult.getContent()) {
            items.add(Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "url", p.getUrl() != null ? p.getUrl() : "",
                "imageUrl", p.getImageUrl() != null ? p.getImageUrl() : "",
                "price", p.getPrice() != null ? p.getPrice() : "",
                "sku", p.getSku() != null ? p.getSku() : "",
                "description", p.getDescription() != null
                        ? p.getDescription().substring(0, Math.min(300, p.getDescription().length())) : "",
                "category", p.getCategory() != null ? p.getCategory() : "",
                "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
            ));
        }
        return ResponseEntity.ok(Map.of(
            "total", pageResult.getTotalElements(),
            "page", page,
            "size", size,
            "totalPages", pageResult.getTotalPages(),
            "items", items
        ));
    }

    /**
     * 重新加载知识库（从 MySQL 重新构建向量索引）
     */
    @PostMapping("/knowledge/reload")
    public ResponseEntity<Map<String, Object>> reloadKnowledge() {
        Map<String, Object> progress = knowledgeBaseLoader.startAsyncReload("manual");
        Map<String, Object> result = new LinkedHashMap<>(progress);
        result.put("success", true);
        result.putIfAbsent("message", "索引更新已启动");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/knowledge/index-progress")
    public ResponseEntity<Map<String, Object>> indexProgress() {
        return ResponseEntity.ok(knowledgeBaseLoader.getIndexProgress());
    }

    /**
     * Agent 广场 — 按操作类型统计会话数量和总调用次数
     */
    @GetMapping("/agent-stats")
    public ResponseEntity<Map<String, Object>> getAgentStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> agents = new LinkedHashMap<>();

        try {
            // 会话数按 operationType 统计
            List<Object[]> typeCounts = sessionRepo.countByOperationType();
            for (Object[] row : typeCounts) {
                String type = (String) row[0];
                if (type == null || type.isBlank()) type = "chat";
                long count = ((Number) row[1]).longValue();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("sessionCount", count);
                agents.put(type, info);
            }

            // 总会话数
            long totalSessions = sessionRepo.count();

            // 总记录数（工具调用次数）
            long totalRecords = 0;
            try {
                totalRecords = recordRepo.count();
            } catch (Exception ignored) {}

            result.put("agents", agents);
            result.put("totalSessions", totalSessions);
            result.put("totalRecords", totalRecords);
        } catch (Exception e) {
            log.warn("查询 Agent 统计失败: {}", e.getMessage());
            result.put("agents", Map.of());
            result.put("totalSessions", 0);
            result.put("totalRecords", 0);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Agent 执行中心 — 聚合近24小时会话记录，返回实时执行状态
     */
    @GetMapping("/execution-status")
    public ResponseEntity<Map<String, Object>> getExecutionStatus() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        LocalDateTime now = LocalDateTime.now();
        List<ConversationRecord> records;

        try {
            records = recordRepo.findByDateRange(since, now);
        } catch (Exception e) {
            log.warn("查询执行状态失败: {}", e.getMessage());
            records = Collections.emptyList();
        }

        // 按 sessionId 分组
        Map<String, List<ConversationRecord>> bySession = records.stream()
                .collect(Collectors.groupingBy(ConversationRecord::getSessionId));

        // 类型 → Agent 名称映射
        Map<String, String> typeNameMap = Map.of(
                "chat", "客服 Agent",
                "inquiry", "询盘评分 Agent",
                "copywriting", "文案生成 Agent",
                "translate", "翻译 Agent",
                "analysis", "分析 Agent",
                "image-recognition", "识图 Agent",
                "seo", "SEO Agent"
        );

        // ── activeAgents ──
        List<Map<String, Object>> activeAgents = new ArrayList<>();
        for (Map.Entry<String, List<ConversationRecord>> entry : bySession.entrySet()) {
            String sid = entry.getKey();
            List<ConversationRecord> sessionRecords = entry.getValue();
            if (sessionRecords.isEmpty()) continue;

            ConversationRecord first = sessionRecords.get(sessionRecords.size() - 1);
            String opType = first.getOperationType();
            if (opType == null || opType.isBlank()) opType = "chat";
            String agentName = typeNameMap.getOrDefault(opType, opType + " Agent");

            // 截取首条用户消息作为任务描述
            String task = "对话中";
            for (ConversationRecord r : sessionRecords) {
                if ("user".equals(r.getRole()) && r.getContent() != null) {
                    task = r.getContent().length() > 30
                            ? r.getContent().substring(0, 30) + "..."
                            : r.getContent();
                    break;
                }
            }

            ConversationRecord lastRec = sessionRecords.get(0); // 按时间倒序，第一条是最新
            Map<String, Object> agent = new LinkedHashMap<>();
            agent.put("name", agentName);
            agent.put("task", task);
            agent.put("type", opType);
            agent.put("sessionId", sid);
            agent.put("messageCount", sessionRecords.size());
            agent.put("lastActiveAt", lastRec.getCreatedAt() != null ? lastRec.getCreatedAt().toString() : "");
            activeAgents.add(agent);
        }
        // 按最近活跃时间排序
        activeAgents.sort((a, b) -> ((String) b.get("lastActiveAt")).compareTo((String) a.get("lastActiveAt")));

        // ── toolCalls ──
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (ConversationRecord r : records) {
            if (r.getToolName() != null && !r.getToolName().isBlank()) {
                Map<String, Object> tc = new LinkedHashMap<>();
                tc.put("id", "tc-" + r.getId());
                tc.put("tool", r.getToolName());
                tc.put("sessionId", r.getSessionId());
                tc.put("status", r.getToolResult() != null
                        && (r.getToolResult().contains("error") || r.getToolResult().contains("失败"))
                        ? "error" : "completed");
                tc.put("startTime", r.getCreatedAt() != null
                        ? r.getCreatedAt().toString().substring(11, 19) : "");
                tc.put("duration", r.getProcessingTimeMs() != null
                        ? String.format("%.1fs", r.getProcessingTimeMs() / 1000.0) : null);
                String result = r.getToolResult();
                if (result != null && result.length() > 30) result = result.substring(0, 30) + "...";
                tc.put("result", result);
                toolCalls.add(tc);
            }
        }

        // ── executionLogs ──
        List<Map<String, Object>> logs = new ArrayList<>();
        int logLimit = Math.min(records.size(), 50);
        for (int i = 0; i < logLimit; i++) {
            ConversationRecord r = records.get(i);
            String opType = r.getOperationType();
            if (opType == null || opType.isBlank()) opType = "chat";
            String agentName = typeNameMap.getOrDefault(opType, opType + " Agent");
            String logType = "info";
            if (r.getToolName() != null && !r.getToolName().isBlank()) {
                logType = r.getToolResult() != null
                        && (r.getToolResult().contains("error") || r.getToolResult().contains("失败"))
                        ? "error" : "success";
            }

            String message = r.getContent();
            if (message == null && r.getToolName() != null) {
                message = "调用工具: " + r.getToolName();
            }
            if (message != null && message.length() > 80) {
                message = message.substring(0, 80) + "...";
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("id", "log-" + r.getId());
            logEntry.put("timestamp", r.getCreatedAt() != null
                    ? r.getCreatedAt().toString().substring(11, 19) : "");
            logEntry.put("type", logType);
            logEntry.put("agent", agentName);
            logEntry.put("message", message != null ? message : "");
            logs.add(logEntry);
        }

        // ── stats ──
        long totalToolCalls = records.stream().filter(r -> r.getToolName() != null && !r.getToolName().isBlank()).count();
        long totalErrs = records.stream().filter(r -> r.getToolResult() != null
                && (r.getToolResult().contains("error") || r.getToolResult().contains("失败"))).count();
        double successRate = toolCalls.isEmpty() ? 100.0
                : Math.round((1.0 - (double) totalErrs / toolCalls.size()) * 1000.0) / 10.0;

        double avgLatency = records.stream()
                .filter(r -> r.getProcessingTimeMs() != null)
                .mapToLong(ConversationRecord::getProcessingTimeMs)
                .average().orElse(0.0);
        String avgLatencyStr = avgLatency > 1000
                ? String.format("%.1fs", avgLatency / 1000)
                : String.format("%.0fms", avgLatency);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeAgents", bySession.size());
        stats.put("toolCalls", toolCalls.size());
        stats.put("successRate", successRate);
        stats.put("avgLatency", avgLatencyStr);

        return ResponseEntity.ok(Map.of(
                "activeAgents", activeAgents,
                "toolCalls", toolCalls,
                "logs", logs,
                "stats", stats
        ));
    }

    /**
     * Infer operationType from session title for old records
     * where operationType was not stored (null in DB). New records
     * created after the fix already have the correct value.
     */
    private String inferType(String title) {
        if (title == null) return "chat";
        String t = title.toLowerCase();
        if (t.contains("inquiry") || t.contains("scoring") || t.contains("询盘")) return "inquiry";
        if (t.contains("translat") || t.contains("翻译"))               return "translate";
        if (t.contains("copywrit") || t.contains("文案") || t.contains("email")) return "copywriting";
        if (t.contains("analysis") || t.contains("分析") || t.contains("market")
                || t.contains("seo") || t.contains("competitor"))       return "analysis";
        if (t.contains("image") || t.contains("识图") || t.contains("recognition")) return "image-recognition";
        return "chat";
    }

    private String currentUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : "user";
    }
}
