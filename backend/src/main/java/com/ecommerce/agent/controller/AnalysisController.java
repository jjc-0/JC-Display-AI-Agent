package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentRuntime;
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
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AgentRuntime agentRuntime;
    private final ConversationManager conversationManager;

    @PostMapping("/market")
    public ResponseEntity<Map<String, Object>> analyzeMarket(@RequestBody Map<String, Object> body) {
        String productName = (String) body.getOrDefault("productName", "");
        String targetCountry = (String) body.getOrDefault("targetCountry", "US");
        long start = System.currentTimeMillis();

        String message = String.format("""
            Analyze the B2B export market opportunity for the following product:
            Product: %s
            Target Market: %s
            
            Provide detailed analysis covering: market size, competitive landscape, pricing strategy,
            entry recommendations, regulatory requirements, and opportunity rating.
            """, productName, targetCountry);

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("analysis")
            .parameters(Map.of("targetCountry", targetCountry))
            .enableTools(true)
            .build();

        try {
            AgentResponse response = agentRuntime.execute(request);
            return ResponseEntity.ok(Map.of(
                "sessionId", response.getSessionId(),
                "result", response.getMessage(),
                "processingTimeMs", response.getProcessingTimeMs()
            ));
        } catch (Exception e) {
            log.error("市场分析失败", e);
            return ResponseEntity.ok(Map.of(
                "sessionId", "",
                "result", "分析服务暂时不可用: " + e.getMessage(),
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }
    }

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getTools() {
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("market_data", Map.of("name", "Market Data", "description", "Real-time market insights", "enabled", true));
        tools.put("currency_convert", Map.of("name", "Currency Converter", "description", "Exchange rate lookup", "enabled", true));
        tools.put("competitor_analysis", Map.of("name", "Competitor Analysis", "description", "Competitive landscape", "enabled", true));
        tools.put("seo_keywords", Map.of("name", "SEO Keywords", "description", "Platform keyword optimization", "enabled", true));
        tools.put("web_scraper", Map.of("name", "Web Scraper", "description", "Market data collection", "enabled", false));
        return ResponseEntity.ok(tools);
    }

    @PostMapping("/seo")
    public ResponseEntity<Map<String, Object>> analyzeSEO(@RequestBody Map<String, Object> body) {
        String url = (String) body.getOrDefault("url", "");
        String keywords = (String) body.getOrDefault("keywords", "");
        String targetCountry = (String) body.getOrDefault("targetCountry", "US");
        long start = System.currentTimeMillis();

        String message = String.format("""
            Perform an SEO audit for the following product page:
            URL: %s
            Target Keywords: %s
            Target Market: %s
            
            Provide comprehensive analysis covering: on-page SEO factors, keyword optimization,
            content quality, technical SEO issues, backlink opportunities, and actionable recommendations.
            Format as a structured report with priority ratings.
            """, url, keywords, targetCountry);

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("analysis_seo")
            .parameters(Map.of("targetCountry", targetCountry))
            .enableTools(true)
            .build();

        try {
            AgentResponse response = agentRuntime.execute(request);
            return ResponseEntity.ok(Map.of(
                "sessionId", response.getSessionId(),
                "result", response.getMessage(),
                "processingTimeMs", response.getProcessingTimeMs()
            ));
        } catch (Exception e) {
            log.error("SEO分析失败", e);
            return ResponseEntity.ok(Map.of(
                "sessionId", "",
                "result", "SEO分析服务暂时不可用: " + e.getMessage(),
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }
    }

    @PostMapping("/competitor")
    public ResponseEntity<Map<String, Object>> analyzeCompetitor(@RequestBody Map<String, Object> body) {
        String competitorUrl = (String) body.getOrDefault("competitorUrl", "");
        String targetCountry = (String) body.getOrDefault("targetCountry", "US");
        String category = (String) body.getOrDefault("category", "floor_display");
        long start = System.currentTimeMillis();

        String message = String.format("""
            Analyze a competitor in the display stand / POP industry:
            Competitor URL: %s
            Target Market: %s
            Product Category: %s
            
            Provide detailed competitive analysis covering: product positioning, pricing strategy,
            strengths and weaknesses, market differentiation, and recommendations for JC Display.
            """, competitorUrl, targetCountry, category);

        AgentRequest request = AgentRequest.builder()
            .message(message)
            .taskType("analysis_competitor")
            .parameters(Map.of("targetCountry", targetCountry))
            .enableTools(true)
            .build();

        try {
            AgentResponse response = agentRuntime.execute(request);
            return ResponseEntity.ok(Map.of(
                "sessionId", response.getSessionId(),
                "result", response.getMessage(),
                "processingTimeMs", response.getProcessingTimeMs()
            ));
        } catch (Exception e) {
            log.error("竞品分析失败", e);
            return ResponseEntity.ok(Map.of(
                "sessionId", "",
                "result", "竞品分析服务暂时不可用: " + e.getMessage(),
                "processingTimeMs", System.currentTimeMillis() - start
            ));
        }
    }
}
