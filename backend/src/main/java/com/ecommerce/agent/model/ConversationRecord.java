package com.ecommerce.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_messages", indexes = {
        @Index(name = "idx_msg_session_id", columnList = "sessionId"),
        @Index(name = "idx_msg_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 30)
    private String operationType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String toolResult;

    @Column(length = 50)
    private String modelUsed;

    @Column
    private Long processingTimeMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
