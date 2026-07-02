package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_artifacts", indexes = {
        @Index(name = "idx_inquiry_artifact_case", columnList = "caseId"),
        @Index(name = "idx_inquiry_artifact_status", columnList = "parseStatus")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 300)
    private String fileName;

    @Column(nullable = false, length = 30)
    private String fileType;

    @Column(nullable = false, length = 30)
    private String sourceType;

    @Column(columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String parseStatus = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String parseError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (parseStatus == null || parseStatus.isBlank()) {
            parseStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
