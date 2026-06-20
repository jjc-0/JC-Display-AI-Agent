package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentDispatcher;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.service.DemoResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final AgentDispatcher agentDispatcher;
    private final ConversationManager conversationManager;
    private final DemoResponseService demoResponseService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> translate(@RequestBody Map<String, Object> body) {
        String text = (String) body.getOrDefault("text", "");
        String sourceLanguage = (String) body.getOrDefault("sourceLanguage", "中文");
        String targetLanguage = (String) body.getOrDefault("targetLanguage", "英文");
        boolean ecommerceLocalization = Boolean.TRUE.equals(body.get("ecommerceLocalization"));
        String context = (String) body.getOrDefault("context", "");

        long start = System.currentTimeMillis();

        String message = String.format("""
            Translate the following text from %s to %s.
            %s
            %s
            Text: %s
            Return only the translated text.
            """,
            sourceLanguage, targetLanguage,
            ecommerceLocalization ? "Apply e-commerce localization: adapt idioms, units, cultural context." : "",
            context.isEmpty() ? "" : "Context: " + context,
            text
        );

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("translation")
            .parameters(Map.of("sourceLanguage", sourceLanguage, "targetLanguage", targetLanguage))
            .enableTools(false)
            .build();

        AgentResponse response;
        try {
            response = agentDispatcher.dispatch(request);
        } catch (Exception e) {
            String fallback = demoResponseService.generateTranslationDemo(text, sourceLanguage, targetLanguage);
            String sessionId = conversationManager.createSession("Translation", "translate");
            return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "result", fallback,
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }

        return ResponseEntity.ok(Map.of(
            "sessionId", response.getSessionId(),
            "result", response.getMessage(),
            "processingTimeMs", response.getProcessingTimeMs()
        ));
    }
}
