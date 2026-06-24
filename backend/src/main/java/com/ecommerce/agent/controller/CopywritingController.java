package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/copywriting")
@RequiredArgsConstructor
public class CopywritingController {

    private final AgentRuntime agentRuntime;
    private final ConversationManager conversationManager;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String productName = (String) body.getOrDefault("productName", "");
        String sellingPoints = (String) body.getOrDefault("sellingPoints", "");
        String targetCountry = (String) body.getOrDefault("targetCountry", "US");
        String platform = (String) body.getOrDefault("platform", "alibaba");
        String style = (String) body.getOrDefault("style", "专业且有吸引力");
        String language = (String) body.getOrDefault("language", "English");

        long start = System.currentTimeMillis();

        String message = buildCopywritingMessage(productName, sellingPoints, targetCountry, platform, style, language);
        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("copywriting")
            .parameters(Map.of("targetCountry", targetCountry, "language", language, "platform", platform))
            .enableTools(false)
            .build();

        try {
            AgentResponse response = agentRuntime.execute(request);
            return ResponseEntity.ok(Map.of(
                "sessionId", response.getSessionId(),
                "result", response.getMessage(),
                "model", response.getModelUsed(),
                "processingTimeMs", response.getProcessingTimeMs()
            ));
        } catch (Exception e) {
            log.error("文案生成失败", e);
            return ResponseEntity.ok(Map.of(
                "sessionId", "",
                "result", "文案生成服务暂时不可用: " + e.getMessage(),
                "model", "error",
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }
    }

    @PostMapping("/generate/collaborative")
    public ResponseEntity<Map<String, Object>> generateCollaborative(@RequestBody Map<String, Object> body) {
        return generate(body);
    }

    private String buildCopywritingMessage(String productName, String sellingPoints, String targetCountry,
                                            String platform, String style, String language) {
        String type = "email".equals(platform) ? "inquiry reply email" : "B2B product detail page for Alibaba/GlobalSources";
        return String.format("""
            Task: Generate a professional %s for the following display stand product.
            
            Product: %s
            Selling Points: %s
            Target Market: %s
            Tone: %s
            Language: %s
            
            Requirements: Professional B2B export language, highlight competitive advantages, include pricing placeholders, structured format.
            """, type, productName, sellingPoints, targetCountry, style, language);
    }
}
