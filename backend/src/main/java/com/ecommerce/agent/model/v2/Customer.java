package com.ecommerce.agent.model.v2;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * v2 客户表 — 外贸核心数据
 * 存储海外买家信息，支持 AI 分析、跟进计划和 CRM 集成
 */
@Entity
@Table(name = "v2_customers", indexes = {
        @Index(name = "idx_customer_website", columnList = "website"),
        @Index(name = "idx_customer_country", columnList = "country"),
        @Index(name = "idx_customer_status", columnList = "status"),
        @Index(name = "idx_customer_industry", columnList = "industry")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 公司名称 */
    @Column(nullable = false, length = 300)
    private String name;

    /** 公司官网 */
    @Column(length = 500)
    private String website;

    /** 国家/地区代码 (US, UK, DE 等) */
    @Column(length = 10)
    private String country;

    /** 行业 (retail, fmcg, electronics 等) */
    @Column(length = 100)
    private String industry;

    /** 客户状态: NEW → CONTACTED → NEGOTIATING → WON → LOST */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "NEW";

    /** 联系人姓名 */
    @Column(length = 200)
    private String contactName;

    /** 联系人邮箱 */
    @Column(length = 300)
    private String contactEmail;

    /** 联系人电话 */
    @Column(length = 50)
    private String contactPhone;

    /** 客户来源 (google_search, trade_show, alibaba, referral 等) */
    @Column(length = 50)
    private String source;

    /** 客户等级 A/B/C */
    @Column(length = 5)
    private String tier;

    /** 预估年采购量 */
    @Column(length = 100)
    private String estimatedVolume;

    /** 上次联系时间 */
    private LocalDateTime lastContactAt;

    /** 下次跟进时间 */
    private LocalDateTime nextFollowUpAt;

    /** AI 分析备注 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String aiNotes;

    /** 产品偏好 (逗号分隔) */
    @Column(length = 500)
    private String productPreferences;

    /** 关键需求摘要 */
    @Column(columnDefinition = "TEXT")
    private String requirements;

    /** 分配给的用户ID */
    @Column(length = 64)
    private String assignedTo;

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
