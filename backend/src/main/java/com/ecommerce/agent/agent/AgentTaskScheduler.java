package com.ecommerce.agent.agent;

import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.model.v2.AgentTask;
import com.ecommerce.agent.repository.AgentTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * v2 任务调度引擎 — 从"聊天工具"到"数字员工"的关键模块
 *
 * 注意: 类名 AgentTaskScheduler 避免与 Spring Boot 内置 TaskScheduler bean 冲突
 *
 * 职责:
 * 1. 定期扫描 pending 状态的任务
 * 2. 按优先级 + 调度时间 排序执行
 * 3. 调用 AgentRuntime.executeTask()
 * 4. 更新任务状态 + 记录执行日志
 * 5. 对定时任务计算下次执行时间
 */
@Slf4j
@Component
@EnableScheduling
public class AgentTaskScheduler {

    private final AgentTaskRepository taskRepo;
    private final AgentRuntime agentRuntime;
    private final ObjectMapper objectMapper;

    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(4);

    public AgentTaskScheduler(AgentTaskRepository taskRepo, AgentRuntime agentRuntime) {
        this.taskRepo = taskRepo;
        this.agentRuntime = agentRuntime;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(fixedDelay = 30000)
    public void scanAndExecute() {
        try {
            List<AgentTask> pendingTasks = taskRepo.findPendingTasks(LocalDateTime.now());
            if (pendingTasks.isEmpty()) return;

            log.info("任务调度器发现 {} 个待执行任务", pendingTasks.size());

            for (AgentTask task : pendingTasks) {
                if ("RUNNING".equals(task.getStatus())) continue;
                taskExecutor.submit(() -> executeTask(task));
            }
        } catch (Exception e) {
            log.error("任务调度器扫描异常", e);
        }
    }

    private void executeTask(AgentTask task) {
        task.setStatus("RUNNING");
        task.setLastRunAt(LocalDateTime.now());
        taskRepo.save(task);

        long startMs = System.currentTimeMillis();
        log.info("开始执行任务: id={}, name={}, type={}", task.getId(), task.getName(), task.getType());

        try {
            AgentResponse response = agentRuntime.executeTask(task);
            long duration = System.currentTimeMillis() - startMs;

            task.setOutput(response.getMessage());
            task.setLastDurationMs(duration);
            task.setExecutionCount(task.getExecutionCount() != null
                    ? task.getExecutionCount() + 1 : 1);

            appendExecutionLog(task, Map.of(
                    "timestamp", LocalDateTime.now().toString(),
                    "durationMs", duration,
                    "status", response.getStatus(),
                    "toolCalls", response.getToolCalls() != null
                            ? response.getToolCalls().size() : 0,
                    "modelUsed", response.getModelUsed()
            ));

            boolean shouldContinue = shouldContinueTask(task);

            if (shouldContinue && task.getScheduleType() != null
                    && task.getScheduleType().contains("SCHEDULED")) {
                task.setNextRunAt(computeNextRun(task));
                task.setStatus("PENDING");
                log.info("任务完成 (定时): id={}, nextRun={}", task.getId(), task.getNextRunAt());
            } else if (shouldContinue) {
                task.setStatus("PENDING");
                task.setNextRunAt(null);
                log.info("任务完成 (等待): id={}", task.getId());
            } else {
                task.setStatus("COMPLETED");
                task.setNextRunAt(null);
                log.info("任务完成 (结束): id={}, duration={}ms", task.getId(), duration);
            }

        } catch (Exception e) {
            log.error("任务执行失败: id={}", task.getId(), e);
            task.setStatus("FAILED");
            task.setOutput("执行失败: " + e.getMessage());
            task.setLastDurationMs(System.currentTimeMillis() - startMs);
            appendExecutionLog(task, Map.of(
                    "timestamp", LocalDateTime.now().toString(),
                    "error", e.getMessage()
            ));
        }

        taskRepo.save(task);
    }

    private boolean shouldContinueTask(AgentTask task) {
        if (task.getMaxExecutions() != null && task.getMaxExecutions() > 0) {
            int count = task.getExecutionCount() != null ? task.getExecutionCount() : 0;
            if (count >= task.getMaxExecutions()) return false;
        }
        return "SCHEDULED".equals(task.getScheduleType())
                || "EVENT_DRIVEN".equals(task.getScheduleType());
    }

    private LocalDateTime computeNextRun(AgentTask task) {
        String cron = task.getCronExpression();
        if (cron == null || cron.isBlank()) {
            return LocalDateTime.now().plusHours(24);
        }
        try {
            if (cron.contains("*/")) {
                String[] parts = cron.split("\\s+");
                for (String part : parts) {
                    if (part.startsWith("*/")) {
                        int hours = Integer.parseInt(part.substring(2));
                        return LocalDateTime.now().plusHours(hours);
                    }
                }
            }
            return LocalDateTime.now().plusHours(24);
        } catch (Exception e) {
            return LocalDateTime.now().plusHours(24);
        }
    }

    private void appendExecutionLog(AgentTask task, Object logEntry) {
        try {
            String existing = task.getExecutionLog() != null ? task.getExecutionLog() : "[]";
            if (existing.endsWith("]")) {
                existing = existing.substring(0, existing.length() - 1);
                if (!existing.endsWith("[")) existing += ",";
            }
            String entry = objectMapper.writeValueAsString(logEntry);
            task.setExecutionLog(existing + entry + "]");
        } catch (JsonProcessingException e) {
            log.warn("序列化执行日志失败", e);
        }
    }

    @PostConstruct
    public void init() {
        log.info("AgentTaskScheduler 已启动 — 每30秒扫描一次待执行任务");
    }

    @PreDestroy
    public void shutdown() {
        log.info("AgentTaskScheduler 正在关闭...");
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AgentTaskScheduler 已关闭");
    }
}
