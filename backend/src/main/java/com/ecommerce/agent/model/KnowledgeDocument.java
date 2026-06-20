package com.ecommerce.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAG 知识库文档 — 持久化到 MySQL
 */
@Entity
@Table(name = "knowledge_documents", indexes = {
        @Index(name = "idx_knowledge_category", columnList = "category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文档标题（如 "公司信息"、"产品规格"） */
    @Column(nullable = false, length = 200)
    private String title;

    /** 文档正文 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 分类标签 */
    @Column(length = 50)
    private String category;

    /** 是否启用（禁用后不会被加载到 RAG） */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** 来源类型: BUILT_IN（内置） / USER_UPLOAD（用户上传） / SCRAPED（爬取） */
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String sourceType = "BUILT_IN";

    /** 原始文件类型: MARKDOWN / PDF / DOCX / TXT */
    @Column(length = 20)
    @Builder.Default
    private String fileType = "MARKDOWN";

    /** 原始文件名（用户上传时保留） */
    @Column(length = 300)
    private String fileName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
