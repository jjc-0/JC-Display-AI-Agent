package com.ecommerce.agent.controller;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/deepseek")
@RequiredArgsConstructor
public class DeepSeekUsageController {

    private final AIConfig aiConfig;
    private final ConversationRecordRepository recordRepository;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsage() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Fetch real balance from DeepSeek
        Map<String, Object> balance = fetchDeepSeekBalance();
        result.put("balance", balance);

        // Aggregate real usage from DB
        Map<String, Object> usage = aggregateDBUsage();
        result.put("usage", usage);

        // Recent calls from DB
        List<Map<String, Object>> recentCalls = getRecentCalls();
        result.put("recentCalls", recentCalls);

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> fetchDeepSeekBalance() {
        Map<String, Object> balance = new LinkedHashMap<>();
        balance.put("totalBalance", 0.0);
        balance.put("currency", "CNY");
        balance.put("isAvailable", false);

        String apiKey = aiConfig.getProviders().getDeepseek().getApiKey();
        if (!aiConfig.isDeepSeekKeyConfigured()) {
            balance.put("error", "DeepSeek API Key 未配置或无效");
            return balance;
        }

        try {
            String baseUrl = aiConfig.getProviders().getDeepseek().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com";
            String url = baseUrl.replaceAll("/+$", "") + "/user/balance";

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonNode root = objectMapper.readTree(response.body().string());
                    balance.put("isAvailable", root.path("is_available").asBoolean(false));

                    JsonNode infos = root.path("balance_infos");
                    if (infos.isArray() && infos.size() > 0) {
                        JsonNode info = infos.get(0);
                        balance.put("currency", info.path("currency").asText("CNY"));
                        double total = Double.parseDouble(info.path("total_balance").asText("0"));
                        double toppedUp = Double.parseDouble(info.path("topped_up_balance").asText("0"));
                        double granted = Double.parseDouble(info.path("granted_balance").asText("0"));
                        balance.put("totalBalance", total);
                        balance.put("toppedUpBalance", toppedUp);
                        balance.put("grantedBalance", granted);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch DeepSeek balance: {}", e.getMessage());
            balance.put("error", "获取余额失败: " + e.getMessage());
        }

        return balance;
    }

    private Map<String, Object> aggregateDBUsage() {
        Map<String, Object> usage = new LinkedHashMap<>();
        try {
            if (recordRepository == null) {
                usage.put("totalCalls", 0);
                usage.put("totalTokens", 0);
                return usage;
            }

            var allRecords = recordRepository.findAll(PageRequest.of(0, Integer.MAX_VALUE, Sort.by("createdAt").descending())).getContent();
            long totalCalls = allRecords.size();
            long todayCalls = allRecords.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                    .count();
            OptionalDouble avgLatency = allRecords.stream()
                    .filter(r -> r.getProcessingTimeMs() != null && r.getProcessingTimeMs() >= 0)
                    .mapToLong(r -> r.getProcessingTimeMs())
                    .average();

            usage.put("totalCalls", totalCalls);
            usage.put("todayCalls", todayCalls);
            usage.put("avgLatencyMs", avgLatency.isPresent() ? Math.round(avgLatency.getAsDouble()) : null);

            // Model breakdown
            Map<String, Long> modelCounts = new LinkedHashMap<>();
            for (var r : allRecords) {
                modelCounts.merge("JC agent", 1L, Long::sum);
            }
            usage.put("modelBreakdown", modelCounts);

        } catch (Exception e) {
            log.warn("Failed to aggregate DB usage: {}", e.getMessage());
            usage.put("totalCalls", 0);
            usage.put("error", e.getMessage());
        }
        return usage;
    }

    private List<Map<String, Object>> getRecentCalls() {
        List<Map<String, Object>> calls = new ArrayList<>();
        try {
            if (recordRepository == null) return calls;

            var records = recordRepository.findAll(PageRequest.of(0, 20, Sort.by("createdAt").descending())).getContent();
            for (var r : records) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", r.getId());
                call.put("time", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
                call.put("model", "JC agent");
                call.put("role", r.getRole());
                call.put("operationType", r.getOperationType());
                call.put("latency", r.getProcessingTimeMs());
                calls.add(call);
            }
        } catch (Exception e) {
            log.warn("Failed to get recent calls: {}", e.getMessage());
        }
        return calls;
    }
}
