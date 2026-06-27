package com.ecommerce.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * v2 工具路由器 — Agent Runtime 的工具调度中枢
 *
 * 职责:
 * 1. 解析 LLM 返回的 tool call → 匹配 Tool
 * 2. 执行工具 + 超时控制
 * 3. 收集执行结果 → 构建上下文反馈给 LLM
 * 4. 支持工具链: toolA 的输出 → toolB 的输入
 */
@Slf4j
@Component
public class ToolRouter {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    /** 工具执行线程池 (独立于 LLM 调用线程) */
    private final ExecutorService toolExecutor = Executors.newFixedThreadPool(8);

    public ToolRouter(ToolRegistry registry) {
        this.registry = registry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行单个工具调用
     * @param llmResponse LLM 返回的含 tool_call 的原始文本
     * @return 工具执行记录
     */
    public ToolCallResult route(String llmResponse) {
        return routeWithContext(llmResponse, Map.of());
    }

    public ToolCallResult routeWithContext(String llmResponse, Map<String, Object> context) {
        String toolCallJson = extractToolCall(llmResponse);
        if (toolCallJson == null) {
            return new ToolCallResult(null, null, null, false, "No tool call found in response");
        }

        try {
            JsonNode node = objectMapper.readTree(toolCallJson);
            String name = node.path("name").asText(null);
            JsonNode argsNode = node.path("arguments");

            if (name == null || name.isBlank()) {
                return new ToolCallResult(null, null, null, false, "Tool name missing");
            }

            Map<String, Object> args = argsNode.isObject()
                    ? objectMapper.convertValue(argsNode, Map.class)
                    : Map.of();

            return execute(name, args, context);

        } catch (Exception e) {
            log.error("解析Tool Call失败: {}", toolCallJson, e);
            return new ToolCallResult("parse_error", null, e.getMessage(), false,
                    "Tool call parse error: " + e.getMessage());
        }
    }

    /**
     * 按名称 + 参数 + 上下文执行工具
     */
    public ToolCallResult execute(String toolName, Map<String, Object> args) {
        return execute(toolName, args, Map.of());
    }

    public ToolCallResult execute(String toolName, Map<String, Object> args, Map<String, Object> context) {
        Tool tool = registry.getTool(toolName);
        if (tool == null) {
            log.warn("工具未注册: {}", toolName);
            return new ToolCallResult(toolName, args, "工具 " + toolName + " 未注册", false,
                    "Tool not registered");
        }

        // 注入上下文参数 (session_id 等)
        Map<String, Object> enrichedArgs = new LinkedHashMap<>(args);
        enrichedArgs.putAll(context);
        enrichedArgs.put("_chain_context", context);

        long start = System.currentTimeMillis();
        try {
            String result = tool.execute(enrichedArgs)
                    .get(tool.getTimeoutMs(), TimeUnit.MILLISECONDS);

            long duration = System.currentTimeMillis() - start;
            if (isBusinessFailure(result)) {
                log.warn("工具业务执行失败: {} result={}", toolName, result);
                return new ToolCallResult(toolName, args, result, false, result, duration);
            }

            log.info("工具执行成功: {} ({}ms), result={} chars",
                    toolName, duration,
                    result != null ? result.length() : 0);

            return new ToolCallResult(toolName, args, result, true, null, duration);

        } catch (TimeoutException e) {
            log.error("工具执行超时: {} ({}ms)", toolName, tool.getTimeoutMs());
            return new ToolCallResult(toolName, args, "工具执行超时", false,
                    "Timeout after " + tool.getTimeoutMs() + "ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return new ToolCallResult(toolName, args, e.getMessage(), false,
                    "Execution error: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean isBusinessFailure(String result) {
        if (result == null || result.isBlank()) return false;
        try {
            JsonNode node = objectMapper.readTree(result);
            return node.path("business_error").asBoolean(false)
                    || (node.has("success") && !node.path("success").asBoolean(true) && node.has("error"));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 批量执行工具链 (顺序)
     * 每个工具的输出会自动注入为下一个工具的上下文
     */
    public List<ToolCallResult> executeChain(List<ToolCallRequest> requests) {
        List<ToolCallResult> results = new ArrayList<>();
        Map<String, Object> chainContext = new LinkedHashMap<>();

        for (ToolCallRequest req : requests) {
            // 注入链上下文
            Map<String, Object> enrichedArgs = new LinkedHashMap<>(req.args());
            enrichedArgs.put("_chain_context", chainContext);

            ToolCallResult result = execute(req.toolName(), enrichedArgs);
            results.add(result);

            if (result.success() && result.output() != null) {
                chainContext.put(req.toolName(), result.output());
            }
            if (!result.success()) {
                log.warn("工具链中断于: {} (第{}/{})", req.toolName(),
                        results.size(), requests.size());
                break;
            }
        }
        return results;
    }

    /**
     * 构建工具执行结果的上下文文本 (给 LLM 理解)
     */
    public String buildToolResultContext(List<ToolCallResult> results) {
        if (results == null || results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            ToolCallResult r = results.get(i);
            sb.append("工具 ").append(i + 1).append(": ").append(r.toolName()).append("\n");
            if (r.success()) {
                String output = r.output();
                if (output != null && output.length() > 2000) {
                    output = output.substring(0, 2000) + "...";
                }
                sb.append("结果: ").append(output).append("\n");
            } else {
                sb.append("错误: ").append(r.error()).append("\n");
            }
            sb.append("---\n");
        }
        return sb.toString();
    }

    /**
     * 按分类获取可用工具列表
     */
    public Map<String, List<Tool>> getToolsByCategory() {
        return registry.getAllTools().stream()
                .filter(Tool::isEnabled)
                .collect(Collectors.groupingBy(Tool::getCategory));
    }

    /**
     * 获取工具推荐 (给 LLM 生成可用工具描述)
     */
    public String buildToolDescriptions() {
        StringBuilder sb = new StringBuilder("可用工具列表:\n\n");
        Map<String, List<Tool>> byCategory = getToolsByCategory();
        for (var entry : byCategory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n");
            for (Tool tool : entry.getValue()) {
                sb.append("- **").append(tool.getName()).append("**: ")
                        .append(tool.getDescription()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── helpers ──

    private String extractToolCall(String llmResponse) {
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

    // ── records ──

    /** 工具调用请求 */
    public record ToolCallRequest(String toolName, Map<String, Object> args) {}

    /** 工具调用结果 */
    public record ToolCallResult(
            String toolName,
            Map<String, Object> input,
            String output,
            boolean success,
            String error,
            long durationMs
    ) {
        public ToolCallResult(String toolName, Map<String, Object> input, String output,
                              boolean success, String error) {
            this(toolName, input, output, success, error, 0);
        }
    }
}
