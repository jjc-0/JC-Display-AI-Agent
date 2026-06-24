package com.ecommerce.agent.agent;

import com.ecommerce.agent.tool.ToolRouter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * v2 工作流引擎 — DAG 执行管道
 *
 * 支持两种模式:
 * 1. 自动模式 (AUTO): Agent Runtime 自主决定执行步骤
 * 2. 流程模式 (DAG): 按预定义的工作流图顺序执行
 *
 * 工作流定义格式 (JSON DAG):
 * {
 *   "nodes": [
 *     {"id": "search",      "tool": "search_customer",   "depends_on": []},
 *     {"id": "analyze",     "tool": "analyze_lead",      "depends_on": ["search"]},
 *     {"id": "generate",    "tool": "generate_email",     "depends_on": ["analyze"]},
 *     {"id": "crm_update",  "tool": "update_customer_status", "depends_on": ["generate"]}
 *   ]
 * }
 */
@Slf4j
@Component
public class WorkflowEngine {

    private final ToolRouter toolRouter;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(ToolRouter toolRouter) {
        this.toolRouter = toolRouter;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行工作流 — DAG 模式
     * @param workflowJson 工作流定义 JSON
     * @param initialInput 初始输入参数
     * @return 每个节点的执行结果
     */
    public WorkflowResult executeDAG(String workflowJson, Map<String, Object> initialInput) {
        long startMs = System.currentTimeMillis();
        List<WorkflowStepResult> stepResults = new ArrayList<>();
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();

        try {
            // 解析工作流定义
            Map<String, Object> workflow = objectMapper.readValue(workflowJson,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflow.get("nodes");

            if (nodes == null || nodes.isEmpty()) {
                return new WorkflowResult(false, "工作流无节点定义", stepResults,
                        System.currentTimeMillis() - startMs);
            }

            // 拓扑排序 (按依赖关系)
            List<WorkflowNode> sorted = topologicalSort(nodes);
            log.info("工作流启动: {} 个节点", sorted.size());

            // 按序执行每个节点
            for (WorkflowNode node : sorted) {
                Map<String, Object> args = resolveNodeArguments(node, initialInput, nodeOutputs);
                log.debug("执行节点: {} tool={} depends={}",
                        node.id, node.tool, node.dependencies);

                com.ecommerce.agent.tool.ToolRouter.ToolCallResult result =
                        toolRouter.execute(node.tool, args);

                nodeOutputs.put(node.id, result.output());

                stepResults.add(new WorkflowStepResult(
                        node.id, node.tool, result.success(),
                        result.output(), result.error(), result.durationMs()));

                // 关键节点失败则中断
                if (!result.success() && !node.optional) {
                    log.warn("工作流中断于节点: {}", node.id);
                    return new WorkflowResult(false,
                            "节点 " + node.id + " 执行失败: " + result.error(),
                            stepResults, System.currentTimeMillis() - startMs);
                }
            }

            log.info("工作流完成: {} 个节点, {}ms", stepResults.size(),
                    System.currentTimeMillis() - startMs);

            return new WorkflowResult(true, "工作流执行成功", stepResults,
                    System.currentTimeMillis() - startMs);

        } catch (Exception e) {
            log.error("工作流执行异常", e);
            return new WorkflowResult(false, "执行异常: " + e.getMessage(),
                    stepResults, System.currentTimeMillis() - startMs);
        }
    }

    /**
     * 拓扑排序 — 按依赖关系排定执行顺序
     */
    private List<WorkflowNode> topologicalSort(List<Map<String, Object>> rawNodes) {
        // 解析节点
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> raw : rawNodes) {
            String id = (String) raw.get("id");
            String tool = (String) raw.get("tool");
            boolean optional = Boolean.TRUE.equals(raw.get("optional"));
            @SuppressWarnings("unchecked")
            List<String> depends = (List<String>) raw.getOrDefault("depends_on", List.of());

            nodeMap.put(id, new WorkflowNode(id, tool, optional, depends));
        }

        // Kahn 算法拓扑排序
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (WorkflowNode node : nodeMap.values()) {
            inDegree.putIfAbsent(node.id, 0);
            for (String dep : node.dependencies) {
                inDegree.merge(node.id, 1, Integer::sum);
                inDegree.putIfAbsent(dep, 0);
            }
        }

        Queue<WorkflowNode> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(nodeMap.get(entry.getKey()));
            }
        }

        List<WorkflowNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            WorkflowNode node = queue.poll();
            sorted.add(node);

            for (WorkflowNode other : nodeMap.values()) {
                if (other.dependencies.contains(node.id)) {
                    int newDegree = inDegree.merge(other.id, -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(other);
                    }
                }
            }
        }

        if (sorted.size() < nodeMap.size()) {
            log.warn("工作流存在循环依赖! sorted={}, total={}", sorted.size(), nodeMap.size());
        }
        return sorted;
    }

    /**
     * 解析节点参数 — 注入初始输入和前置节点输出
     */
    private Map<String, Object> resolveNodeArguments(WorkflowNode node,
                                                      Map<String, Object> initialInput,
                                                      Map<String, Object> nodeOutputs) {
        Map<String, Object> args = new LinkedHashMap<>(initialInput);

        // 注入前置节点输出
        for (String dep : node.dependencies) {
            Object output = nodeOutputs.get(dep);
            if (output != null) {
                args.put("_input_from_" + dep, output);
            }
        }

        return args;
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════

    static class WorkflowNode {
        final String id;
        final String tool;
        final boolean optional;
        final List<String> dependencies;

        WorkflowNode(String id, String tool, boolean optional, List<String> dependencies) {
            this.id = id;
            this.tool = tool;
            this.optional = optional;
            this.dependencies = dependencies != null ? dependencies : List.of();
        }
    }

    public record WorkflowStepResult(
            String nodeId,
            String toolName,
            boolean success,
            String output,
            String error,
            long durationMs
    ) {}

    public record WorkflowResult(
            boolean success,
            String summary,
            List<WorkflowStepResult> steps,
            long totalDurationMs
    ) {}
}
