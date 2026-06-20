package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentDispatcher;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.llm.PromptTemplateManager;
import com.ecommerce.agent.model.*;
import com.ecommerce.agent.service.DemoResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/copywriting")
@RequiredArgsConstructor
public class CopywritingController {

    private final AgentDispatcher agentDispatcher;
    private final ConversationManager conversationManager;
    private final DemoResponseService demoResponseService;

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

        AgentResponse response;
        try {
            response = agentDispatcher.dispatch(request);
        } catch (Exception e) {
            String fallback = demoResponseService.generateCopywritingDemo(productName, sellingPoints, platform, targetCountry);
            String sessionId = conversationManager.createSession("Copywriting", "copywriting");
            return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "result", fallback,
                "model", "demo-mode",
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }

        return ResponseEntity.ok(Map.of(
            "sessionId", response.getSessionId(),
            "result", response.getMessage(),
            "model", response.getModelUsed(),
            "processingTimeMs", response.getProcessingTimeMs()
        ));
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
