package com.ecommerce.agent.model.v2;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * v2 任务表 — Agent OS 的调度核心
 *
 * 支持:
 * - 一次性任务 (ONE_TIME): 立即执行
 * - 定时任务 (SCHEDULED): cron/周期性
 * - 事件驱动 (EVENT_DRIVEN): 由 webhook/外部触发
 *
 * 任务生命周期: PENDING → RUNNING → COMPLETED / FAILED
 */
@Entity
@Table(name = "v2_tasks", indexes = {
        @Index(name = "idx_task_user", columnList = "userId"),
        @Index(name = "idx_task_status", columnList = "status"),
        @Index(name = "idx_task_type", columnList = "type"),
        @Index(name = "idx_task_next_run", columnList = "nextRunAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(nullable = false, length = 64)
    private String userId;

    /** 任务名称 */
    @Column(nullable = false, length = 300)
    private String name;

    /** 任务类型 */
    @Column(nullable = false, length = 50)
    private String type;

    /**
     * 调度类型:
     * - ONE_TIME: 一次性执行
     * - SCHEDULED: 定时 (cron)
     * - EVENT_DRIVEN: 事件触发
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String scheduleType = "ONE_TIME";

    /** Cron 表达式 (scheduleType=SCHEDULED 时使用) */
    @Column(length = 100)
    private String cronExpression;

    /** 任务状态: PENDING → RUNNING → COMPLETED / FAILED / PAUSED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** 任务优先级 1-10, 10最高 */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 5;

    /** 任务描述 (给 AI 理解任务目标) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 任务上下文参数 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String context;

    /** 执行时使用的 Agent 类型 */
    @Column(length = 50)
    private String agentType;

    /** 执行时使用的工具列表 (逗号分隔) */
    @Column(length = 500)
    private String toolList;

    /**
     * 工作流定义 (JSON DAG)
     * 定义每个步骤的依赖关系和执行顺序
     */
    @Column(columnDefinition = "TEXT")
    private String workflowDefinition;

    /** 任务输入参数 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String input;

    /** 任务执行结果 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String output;

    /** 执行日志 (JSON Array) */
    @Column(columnDefinition = "TEXT")
    private String executionLog;

    /** 执行次数 */
    @Column(nullable = false)
    @Builder.Default
    private Integer executionCount = 0;

    /** 最大执行次数 (0 = 无限) */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxExecutions = 1;

    /** 上次执行时间 */
    private LocalDateTime lastRunAt;

    /** 下次执行时间 */
    private LocalDateTime nextRunAt;

    /** 上次执行耗时(ms) */
    private Long lastDurationMs;

    /** 关联的会话ID */
    @Column(length = 64)
    private String sessionId;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nextRunAt == null && "ONE_TIME".equals(scheduleType)) {
            nextRunAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
