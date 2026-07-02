package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_requirements", indexes = {
        @Index(name = "idx_inquiry_requirement_case", columnList = "caseId"),
        @Index(name = "idx_inquiry_requirement_field", columnList = "fieldKey")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 80)
    private String fieldKey;

    @Column(nullable = false, length = 120)
    private String fieldLabel;

    @Column(columnDefinition = "TEXT")
    private String fieldValue;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(columnDefinition = "TEXT")
    private String sourceEvidence;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "AI_EXTRACTED";

    @Column(length = 64)
    private String updatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "AI_EXTRACTED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
