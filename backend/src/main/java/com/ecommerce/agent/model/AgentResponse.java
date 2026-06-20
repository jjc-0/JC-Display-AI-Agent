package com.ecommerce.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private String sessionId;
    private String message;
    private String taskType;
    private String status;
    private List<ToolCallRecord> toolCalls;
    private Map<String, Object> metadata;
    private long processingTimeMs;
    private String modelUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRecord {
        private String toolName;
        private String input;
        private String output;
        private String status;
        private long durationMs;
    }
}
