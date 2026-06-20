package com.ecommerce.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private String role;
    private String content;
    private String toolName;
    private String toolResult;
    private long timestamp;
}
