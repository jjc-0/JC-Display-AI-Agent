package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.*;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.model.ConversationMessage;
import com.ecommerce.agent.model.v2.AgentTask;
import com.ecommerce.agent.model.v2.Customer;
import com.ecommerce.agent.model.v2.MemoryEntry;
import com.ecommerce.agent.repository.AgentTaskRepository;
import com.ecommerce.agent.repository.CustomerRepository;
import com.ecommerce.agent.repository.MemoryRepository;
import com.ecommerce.agent.service.v2.MemoryService;
import com.ecommerce.agent.tool.ToolRegistry;
import com.ecommerce.agent.tool.ToolRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * v2 Agent OS 统一入口 — 新架构的所有 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v2")
public class V2AgentController {

    private final AgentRuntime agentRuntime;
    private final AgentTaskScheduler taskScheduler;
    private final WorkflowEngine workflowEngine;
    private final MemoryService memoryService;
    private final MemoryRepository memoryRepo;
    private final AgentTaskRepository taskRepo;
    private final CustomerRepository customerRepo;
    private final ToolRouter toolRouter;
    private final ToolRegistry toolRegistry;
    private final ConversationManager conversationManager;

    public V2AgentController(AgentRuntime agentRuntime,
                             AgentTaskScheduler taskScheduler,
                             WorkflowEngine workflowEngine,
                             MemoryService memoryService,
                             MemoryRepository memoryRepo,
                             AgentTaskRepository taskRepo,
                             CustomerRepository customerRepo,
                             ToolRouter toolRouter,
                             ToolRegistry toolRegistry,
                             ConversationManager conversationManager) {
        this.agentRuntime = agentRuntime;
        this.taskScheduler = taskScheduler;
        this.workflowEngine = workflowEngine;
        this.memoryService = memoryService;
        this.memoryRepo = memoryRepo;
        this.taskRepo = taskRepo;
        this.customerRepo = customerRepo;
        this.toolRouter = toolRouter;
        this.toolRegistry = toolRegistry;
        this.conversationManager = conversationManager;
    }

    // ═══════════════════════════════════════════════════════════════
    // Agent Runtime
    // ═══════════════════════════════════════════════════════════════

    /** v2 核心对话 — 使用 AgentRuntime while(!done) 循环 */
    @PostMapping("/agent/run")
    public ResponseEntity<Map<String, Object>> agentRun(@RequestBody Map<String, Object> body,
                                                        Authentication authentication) {
        String message = (String) body.getOrDefault("message", "");
        String sessionId = (String) body.getOrDefault("sessionId", null);
        boolean enableTools = Boolean.TRUE.equals(body.get("enableTools"));
        String customerId = (String) body.get("customerId");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = new LinkedHashMap<>();
        if (customerId != null) params.put("customerId", customerId);
        // 透传前端上报的所有 parameters (如图片 _images 等)
        Object frontendParams = body.get("parameters");
        if (frontendParams instanceof Map<?, ?> fp) {
            ((Map<String, Object>) fp).forEach((k, v) -> {
                if (!params.containsKey(k)) params.put(k, v);
            });
        }

        AgentRequest request = AgentRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .taskType("chat")
                .enableTools(enableTools)
                .parameters(params)
                .userId(currentUsername(authentication))
                .username(currentUsername(authentication))
                .build();

        AgentResponse response = agentRuntime.execute(request);

        return ResponseEntity.ok(toAgentResponseMap(response));
    }

    /** 工作流执行 */
    @PostMapping("/agent/workflow")
    public ResponseEntity<Map<String, Object>> runWorkflow(@RequestBody Map<String, Object> body) {
        String workflowJson = (String) body.getOrDefault("workflow", "{}");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) body.getOrDefault("input", Map.of());

        WorkflowEngine.WorkflowResult result = workflowEngine.executeDAG(workflowJson, input);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("summary", result.summary());
        response.put("totalDurationMs", result.totalDurationMs());

        List<Map<String, Object>> steps = new ArrayList<>();
        for (var s : result.steps()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("nodeId", s.nodeId());
            sm.put("toolName", s.toolName());
            sm.put("success", s.success());
            sm.put("output", s.output());
            sm.put("error", s.error());
            sm.put("durationMs", s.durationMs());
            steps.add(sm);
        }
        response.put("steps", steps);

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════════
    // Task 管理
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String type = (String) body.getOrDefault("type", "custom");
        String description = (String) body.get("description");
        String scheduleType = (String) body.getOrDefault("scheduleType", "ONE_TIME");
        String cronExpression = (String) body.get("cronExpression");
        String context = body.get("context") != null ? body.get("context").toString() : null;
        String toolList = (String) body.get("toolList");
        String workflowDef = (String) body.get("workflow");
        String input = body.get("input") != null ? body.get("input").toString() : null;
        int maxExecutions = body.get("maxExecutions") != null
                ? Integer.parseInt(body.get("maxExecutions").toString()) : 1;
        int priority = body.get("priority") != null
                ? Integer.parseInt(body.get("priority").toString()) : 5;

        AgentTask task = AgentTask.builder()
                .userId((String) body.getOrDefault("userId", "user"))
                .name(name)
                .type(type)
                .description(description)
                .scheduleType(scheduleType)
                .cronExpression(cronExpression)
                .context(context)
                .toolList(toolList)
                .workflowDefinition(workflowDef)
                .input(input)
                .maxExecutions(maxExecutions)
                .priority(priority)
                .status("PENDING")
                .nextRunAt("ONE_TIME".equals(scheduleType) ? LocalDateTime.now() : null)
                .build();

        task = taskRepo.save(task);
        return ResponseEntity.ok(Map.of("success", true, "task", toTaskMap(task)));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status) {
        List<AgentTask> tasks;
        if (status != null && !status.isBlank()) {
            tasks = taskRepo.findByStatusOrderByPriorityDesc(status);
        } else if (userId != null) {
            tasks = taskRepo.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            tasks = taskRepo.findAll();
        }
        return ResponseEntity.ok(Map.of("tasks", tasks.stream().map(this::toTaskMap).toList()));
    }

    @PostMapping("/tasks/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeTaskNow(@PathVariable Long id) {
        AgentTask task = taskRepo.findById(id).orElse(null);
        if (task == null) return ResponseEntity.notFound().build();

        new Thread(() -> taskScheduler.scanAndExecute()).start();

        return ResponseEntity.ok(Map.of("success", true, "message", "任务已加入执行队列"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Customer 管理
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> createCustomer(@RequestBody Map<String, Object> body) {
        Customer customer = Customer.builder()
                .name((String) body.get("name"))
                .website((String) body.get("website"))
                .country((String) body.get("country"))
                .industry((String) body.get("industry"))
                .contactName((String) body.get("contactName"))
                .contactEmail((String) body.get("contactEmail"))
                .contactPhone((String) body.get("contactPhone"))
                .source((String) body.getOrDefault("source", "manual"))
                .status("NEW")
                .assignedTo((String) body.getOrDefault("assignedTo", "user"))
                .productPreferences((String) body.get("productPreferences"))
                .requirements((String) body.get("requirements"))
                .build();

        customer = customerRepo.save(customer);
        return ResponseEntity.ok(Map.of("success", true, "customer", toCustomerMap(customer)));
    }

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> listCustomers(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<Customer> customers;
        if (keyword != null && !keyword.isBlank()) {
            customers = customerRepo.search(keyword);
        } else if (country != null) {
            customers = customerRepo.findByCountryOrderByNameAsc(country);
        } else if (status != null) {
            customers = customerRepo.findByStatusOrderByUpdatedAtDesc(status);
        } else {
            customers = customerRepo.findAll();
        }
        return ResponseEntity.ok(Map.of("customers",
                customers.stream().map(this::toCustomerMap).toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // Memory 查询
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/memories")
    public ResponseEntity<Map<String, Object>> queryMemories(
            @RequestParam(defaultValue = "user") String userId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<MemoryEntry> memories;
        if (query != null && !query.isBlank()) {
            List<MemoryService.MemoryHit> hits = memoryService.vectorSearch(userId, query, topK);
            memories = hits.stream().map(MemoryService.MemoryHit::entry).toList();
        } else {
            memories = memoryRepo.findByUserIdAndArchivedFalseOrderByImportanceDesc(userId);
        }
        return ResponseEntity.ok(Map.of("memories",
                memories.stream().map(this::toMemoryMap).toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // Tool Registry
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        toolRouter.getToolsByCategory().forEach((cat, tools) -> {
            byCategory.put(cat, tools.stream().map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("name", t.getName());
                tm.put("description", t.getDescription());
                tm.put("category", t.getCategory());
                tm.put("enabled", t.isEnabled());
                return tm;
            }).toList());
        });
        return ResponseEntity.ok(Map.of("tools", byCategory,
                "totalCount", toolRegistry.getAllTools().size()));
    }

    // ═══════════════════════════════════════════════════════════════
    // Session (复用 ConversationManager)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String sessionId,
                                                          Authentication authentication) {
        if (!conversationManager.isSessionOwnedBy(sessionId, currentUsername(authentication)) && !isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权查看该对话记录"));
        }
        List<ConversationMessage> history = conversationManager.getHistory(sessionId);
        return ResponseEntity.ok(Map.of("records", history.stream().map(msg -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("toolName", msg.getToolName());
            m.put("toolResult", msg.getToolResult());
            return m;
        }).toList()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getMySessions(@RequestParam(required = false) String type,
                                                             Authentication authentication) {
        String username = currentUsername(authentication);
        return ResponseEntity.ok(Map.of("sessions", conversationManager.getSessionListForUser(username, type)));
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> toAgentResponseMap(AgentResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", r.getSessionId());
        m.put("message", r.getMessage());
        m.put("status", r.getStatus());
        m.put("modelUsed", r.getModelUsed());
        m.put("processingTimeMs", r.getProcessingTimeMs());

        List<Map<String, Object>> tc = new ArrayList<>();
        if (r.getToolCalls() != null) {
            for (var t : r.getToolCalls()) {
                Map<String, Object> tcm = new LinkedHashMap<>();
                tcm.put("toolName", t.getToolName());
                tcm.put("output", t.getOutput());
                tcm.put("status", t.getStatus());
                tcm.put("durationMs", t.getDurationMs());
                tc.add(tcm);
            }
        }
        m.put("toolCalls", tc);
        if (r.getMetadata() != null) m.putAll(r.getMetadata());
        return m;
    }

    private String currentUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : "user";
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Map<String, Object> toTaskMap(AgentTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("type", t.getType());
        m.put("status", t.getStatus());
        m.put("priority", t.getPriority());
        m.put("scheduleType", t.getScheduleType());
        m.put("cronExpression", t.getCronExpression());
        m.put("executionCount", t.getExecutionCount());
        m.put("maxExecutions", t.getMaxExecutions());
        m.put("lastRunAt", t.getLastRunAt() != null ? t.getLastRunAt().toString() : null);
        m.put("nextRunAt", t.getNextRunAt() != null ? t.getNextRunAt().toString() : null);
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toCustomerMap(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("website", c.getWebsite());
        m.put("country", c.getCountry());
        m.put("industry", c.getIndustry());
        m.put("status", c.getStatus());
        m.put("contactName", c.getContactName());
        m.put("contactEmail", c.getContactEmail());
        m.put("source", c.getSource());
        m.put("tier", c.getTier());
        m.put("nextFollowUpAt", c.getNextFollowUpAt() != null ? c.getNextFollowUpAt().toString() : null);
        return m;
    }

    private Map<String, Object> toMemoryMap(MemoryEntry m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("type", m.getType());
        map.put("summary", m.getSummary());
        map.put("importance", m.getImportance());
        map.put("tags", m.getTags());
        map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return map;
    }
}
