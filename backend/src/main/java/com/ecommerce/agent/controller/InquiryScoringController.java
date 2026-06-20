package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentDispatcher;
import com.ecommerce.agent.agent.ConversationManager;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inquiry")
@RequiredArgsConstructor
public class InquiryScoringController {

    private final AgentDispatcher agentDispatcher;
    private final ConversationManager conversationManager;

    @PostMapping("/score")
    public ResponseEntity<Map<String, Object>> scoreInquiry(@RequestBody Map<String, Object> body) {
        String customerName = (String) body.getOrDefault("customerName", "匿名客户");
        String customerCountry = (String) body.getOrDefault("customerCountry", "");
        String inquiryText = (String) body.getOrDefault("inquiryText", "");

        long start = System.currentTimeMillis();

        String countryInfo = customerCountry.isEmpty() ? "" : "客户国家/地区: " + customerCountry + "\n";
        String message = String.format("""
            You are a B2B export inquiry scoring expert. Analyze the following inquiry and provide a structured evaluation.

            Customer: %s
            %s
            Inquiry Content:
            %s

            Return a JSON with the following fields (Chinese):
            {
              "score": <0-100 integer>,
              "intent": "<产品咨询|批量采购|样品请求|价格询问|合作意向|简单问候>",
              "buyerStage": "<初步意向|比较询价|决策阶段|老客户>",
              "quantity": "<estimated quantity or 未明确>",
              "urgency": "<高|中|低>",
              "reason": "<detailed scoring reason in Chinese>",
              "suggestedReply": "<suggested reply strategy in Chinese>"
            }

            Score criteria:
            - Higher score for detailed product specs mention, quantity mentioned, company info provided
            - Lower score for vague/generic inquiries
            - Penalty for one-liner messages without details
            Only return the JSON, no other text.
            """, customerName, countryInfo, inquiryText);

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("inquiry_scoring")
            .parameters(Map.of("customerCountry", customerCountry))
            .enableTools(false)
            .build();

        AgentResponse response;
        try {
            response = agentDispatcher.dispatch(request);
            String resultText = response.getMessage();

            // Try to parse JSON from response (it might be wrapped in markdown)
            Map<String, Object> parsed = parseInquiryResult(resultText);
            parsed.put("sessionId", response.getSessionId());
            parsed.put("processingTimeMs", response.getProcessingTimeMs());
            parsed.put("customerName", customerName);
            parsed.put("customerCountry", customerCountry);
            parsed.put("inquiryText", inquiryText);
            return ResponseEntity.ok(parsed);

        } catch (Exception e) {
            log.error("Inquiry scoring failed", e);
            int fallbackScore = 40 + (int)(Math.random() * 50);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("sessionId", conversationManager.createSession("Inquiry Scoring", "inquiry"));
            fallback.put("score", fallbackScore);
            fallback.put("intent", "产品咨询");
            fallback.put("buyerStage", "初步意向");
            fallback.put("quantity", "未明确");
            fallback.put("urgency", "中");
            fallback.put("reason", "AI 服务暂时不可用，显示为预估评分。");
            fallback.put("suggestedReply", "建议等待 AI 服务恢复后重新分析，或手动评估询盘。");
            fallback.put("processingTimeMs", System.currentTimeMillis() - start);
            fallback.put("customerName", customerName);
            fallback.put("customerCountry", customerCountry);
            fallback.put("inquiryText", inquiryText);
            return ResponseEntity.ok(fallback);
        }
    }

    private Map<String, Object> parseInquiryResult(String text) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Defaults
        result.put("score", 50);
        result.put("intent", "产品咨询");
        result.put("buyerStage", "初步意向");
        result.put("quantity", "未明确");
        result.put("urgency", "中");
        result.put("reason", "");
        result.put("suggestedReply", "");

        if (text == null || text.isEmpty()) return result;

        try {
            // Try direct JSON parse (strip markdown if any)
            String jsonStr = text.trim();
            if (jsonStr.startsWith("```")) {
                int start = jsonStr.indexOf("{");
                int end = jsonStr.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1);
                }
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(jsonStr, Map.class);

            if (parsed.containsKey("score")) result.put("score", parsed.get("score"));
            if (parsed.containsKey("intent")) result.put("intent", parsed.get("intent"));
            if (parsed.containsKey("buyerStage")) result.put("buyerStage", parsed.get("buyerStage"));
            if (parsed.containsKey("quantity")) result.put("quantity", parsed.get("quantity"));
            if (parsed.containsKey("urgency")) result.put("urgency", parsed.get("urgency"));
            if (parsed.containsKey("reason")) result.put("reason", parsed.get("reason"));
            if (parsed.containsKey("suggestedReply")) result.put("suggestedReply", parsed.get("suggestedReply"));

        } catch (Exception e) {
            log.warn("Failed to parse inquiry JSON, using raw extract. Error: {}", e.getMessage());
            result.put("reason", text);
        }

        return result;
    }
}
