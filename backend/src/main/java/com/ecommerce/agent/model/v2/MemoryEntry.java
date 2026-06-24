package com.ecommerce.agent.model.v2;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * v2 长期记忆表 — Agent OS 的记忆系统核心
 *
 * 存储三类记忆:
 * - CUSTOMER: 客户信息、偏好、历史询盘摘要
 * - TASK: 任务执行上下文、中间结果
 * - KNOWLEDGE: 用户上传的知识片段
 *
 * 配合 pgvector / FAISS 做向量检索
 */
@Entity
@Table(name = "v2_memories", indexes = {
        @Index(name = "idx_memory_user", columnList = "userId"),
        @Index(name = "idx_memory_type", columnList = "type"),
        @Index(name = "idx_memory_customer", columnList = "customerId"),
        @Index(name = "idx_memory_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 (多租户) */
    @Column(length = 64)
    private String userId;

    /** 关联的客户ID (可以为空) */
    @Column(length = 20)
    private String customerId;

    /** 记忆类型: CUSTOMER / TASK / KNOWLEDGE */
    @Column(nullable = false, length = 20)
    private String type;

    /** 记忆内容摘要 (用于列表展示) */
    @Column(nullable = false, length = 500)
    private String summary;

    /** 记忆完整内容 (JSON 或 文本) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 向量化后的 embedding (Base64 编码, 临时用 TEXT 存储) */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String embedding;

    /** 关键标签 (逗号分隔, 用于过滤) */
    @Column(length = 500)
    private String tags;

    /** 来源 sessionId */
    @Column(length = 64)
    private String sourceSessionId;

    /** 重要性评分 0-10 */
    @Column(nullable = false)
    @Builder.Default
    private Integer importance = 5;

    /** 访问次数 (越高越常被引用) */
    @Column(nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    /** 是否已归档 */
    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    /** 过期时间 (null = 永不过期) */
    private LocalDateTime expiresAt;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
