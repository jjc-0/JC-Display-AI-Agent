package com.ecommerce.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "jc_claw_channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JCClawChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String type; // wechat, whatsapp, email, etc.

    @Column(length = 200)
    private String agentId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "unbound"; // unbound, binding, bound

    @Column(length = 500)
    private String qrCodeUrl;

    @Column(length = 100)
    private String bindingKey; // unique key for identifying bind session

    @Column(length = 100)
    private String openUserId; // 微信 OpenID

    @Column(nullable = false)
    @Builder.Default
    private int userCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime boundAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
