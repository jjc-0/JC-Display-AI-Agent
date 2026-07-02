package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_missing_fields", indexes = {
        @Index(name = "idx_inquiry_missing_case", columnList = "caseId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 80)
    private String fieldKey;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String questionEn;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (priority == null || priority.isBlank()) {
            priority = "MEDIUM";
        }
    }
}
