package com.ecommerce.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class CurrencyTool implements Tool {

    private static final String API_URL = "https://api.exchangerate-api.com/v4/latest/";
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "currency";
    }

    @Override
    public String getCategory() { return "INFO"; }

    @Override
    public String getDescription() {
        return "汇率换算工具，支持多种货币之间的汇率转换。输入源货币和目标货币代码，返回实时汇率和换算结果。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> fromProp = new LinkedHashMap<>();
        fromProp.put("type", "string");
        fromProp.put("description", "源货币代码，如CNY、USD、EUR、JPY等");
        props.put("from", fromProp);

        Map<String, Object> toProp = new LinkedHashMap<>();
        toProp.put("type", "string");
        toProp.put("description", "目标货币代码，如CNY、USD、EUR、JPY等");
        props.put("to", toProp);

        Map<String, Object> amountProp = new LinkedHashMap<>();
        amountProp.put("type", "number");
        amountProp.put("description", "要换算的金额");
        props.put("amount", amountProp);

        schema.put("properties", props);
        schema.put("required", List.of("from", "to", "amount"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String from = (String) params.getOrDefault("from", "USD");
                String to = (String) params.getOrDefault("to", "CNY");
                double amount = Double.parseDouble(params.getOrDefault("amount", "1").toString());

                Request request = new Request.Builder()
                        .url(API_URL + from.toUpperCase())
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return "汇率查询失败: HTTP " + response.code();
                    }
                    JsonNode root = objectMapper.readTree(response.body().string());
                    JsonNode rates = root.get("rates");
                    if (rates == null || !rates.has(to.toUpperCase())) {
                        return String.format("无法获取 %s 到 %s 的汇率", from, to);
                    }
                    double rate = rates.get(to.toUpperCase()).asDouble();
                    double result = amount * rate;

                    return String.format("""
                            💱 汇率换算结果:
                            - 源货币: %s %.2f
                            - 目标货币: %s %.2f
                            - 实时汇率: 1 %s = %.4f %s
                            - 更新时间: %s
                            """,
                            from.toUpperCase(), amount,
                            to.toUpperCase(), result,
                            from.toUpperCase(), rate, to.toUpperCase(),
                            root.get("date").asText());
                }
            } catch (Exception e) {
                log.error("Currency tool error", e);
                return "汇率查询失败: " + e.getMessage();
            }
        });
    }
}
