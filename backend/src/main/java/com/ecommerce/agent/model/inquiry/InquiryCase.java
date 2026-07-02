package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_cases", indexes = {
        @Index(name = "idx_inquiry_case_owner", columnList = "ownerId"),
        @Index(name = "idx_inquiry_case_status", columnList = "status"),
        @Index(name = "idx_inquiry_case_updated", columnList = "updatedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String caseNo;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String customerName;

    @Column(length = 200)
    private String contactName;

    @Column(length = 300)
    private String contactEmail;

    @Column(length = 80)
    private String country;

    @Column(length = 80)
    private String source;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "DRAFT";

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 64)
    private String ownerId;

    @Column(length = 120)
    private String ownerName;

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
