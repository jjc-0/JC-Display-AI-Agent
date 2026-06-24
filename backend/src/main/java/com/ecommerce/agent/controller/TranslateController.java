package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final AgentRuntime agentRuntime;
    private final ConversationManager conversationManager;

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

        try {
            AgentResponse response = agentRuntime.execute(request);
            return ResponseEntity.ok(Map.of(
                "sessionId", response.getSessionId(),
                "result", response.getMessage(),
                "processingTimeMs", response.getProcessingTimeMs()
            ));
        } catch (Exception e) {
            log.error("翻译失败", e);
            return ResponseEntity.ok(Map.of(
                "sessionId", "",
                "result", "翻译服务暂时不可用: " + e.getMessage(),
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }
    }
}
