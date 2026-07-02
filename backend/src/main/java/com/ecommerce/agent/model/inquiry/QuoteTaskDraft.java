package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_quote_task_drafts", indexes = {
        @Index(name = "idx_quote_task_case", columnList = "caseId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteTaskDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 300)
    private String taskTitle;

    @Column(columnDefinition = "TEXT")
    private String knownInfo;

    @Column(columnDefinition = "TEXT")
    private String missingInfo;

    @Column(columnDefinition = "TEXT")
    private String riskSummary;

    @Column(length = 40)
    private String assigneeRole;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(columnDefinition = "TEXT")
    private String emailDraft;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
