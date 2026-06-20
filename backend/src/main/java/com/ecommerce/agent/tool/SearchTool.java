package com.ecommerce.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class SearchTool implements Tool {

    @Value("${tools.search.google-api-key:}")
    private String googleApiKey;

    @Value("${tools.search.google-cx:}")
    private String googleCx;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String getDescription() {
        return "网络搜索工具，用于搜索商品信息、市场价格、竞品分析等。输入搜索关键词，返回搜索结果摘要。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索关键词或短语");
        props.put("query", queryProp);

        Map<String, Object> numProp = new LinkedHashMap<>();
        numProp.put("type", "integer");
        numProp.put("description", "返回结果数量，默认5条");
        props.put("num", numProp);

        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = (String) params.getOrDefault("query", "");
                int num = Integer.parseInt(params.getOrDefault("num", "5").toString());

                if (googleApiKey.isEmpty() || googleCx.isEmpty()) {
                    return simulateSearch(query, num);
                }

                String url = String.format(
                        "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=%d",
                        googleApiKey, googleCx, java.net.URLEncoder.encode(query, "UTF-8"), Math.min(num, 10));

                Request request = new Request.Builder().url(url).get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return simulateSearch(query, num);
                    }
                    JsonNode root = objectMapper.readTree(response.body().string());
                    JsonNode items = root.get("items");
                    if (items == null || items.isEmpty()) {
                        return "未找到相关搜索结果。";
                    }

                    StringBuilder sb = new StringBuilder("🔍 搜索结果:\n\n");
                    for (int i = 0; i < Math.min(items.size(), num); i++) {
                        JsonNode item = items.get(i);
                        sb.append(i + 1).append(". **")
                                .append(item.get("title").asText())
                                .append("**\n")
                                .append("   ").append(item.get("snippet").asText())
                                .append("\n   ").append(item.get("link").asText())
                                .append("\n\n");
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                log.error("Search tool error", e);
                return "搜索功能暂时不可用: " + e.getMessage();
            }
        });
    }

    private String simulateSearch(String query, int num) {
        StringBuilder sb = new StringBuilder("🔍 搜索结果 (模拟):\n\n");
        sb.append("关于 \"").append(query).append("\" 的跨境电商相关信息:\n\n");

        String[] simulatedResults = {
                "亚马逊/Amazon - 全球最大的跨境电商平台，覆盖北美、欧洲、日本等市场",
                "速卖通/AliExpress - 阿里巴巴旗下跨境零售平台，适合中小企业出海",
                "TikTok Shop - 社交电商平台，通过短视频和直播带货",
                "Shopify - 独立站建站平台，支持多语言、多货币",
                "Google Trends - 分析产品在不同国家/地区的搜索热度趋势"
        };

        for (int i = 0; i < Math.min(num, simulatedResults.length); i++) {
            sb.append(i + 1).append(". ").append(simulatedResults[i]).append("\n\n");
        }
        sb.append("💡 提示: 配置Google Search API后可获取实时搜索结果。");
        return sb.toString();
    }
}
