package com.ecommerce.agent.model.inquiry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_risk_flags", indexes = {
        @Index(name = "idx_inquiry_risk_case", columnList = "caseId"),
        @Index(name = "idx_inquiry_risk_level", columnList = "level")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 40)
    private String riskType;

    @Column(nullable = false, length = 20)
    private String level;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
