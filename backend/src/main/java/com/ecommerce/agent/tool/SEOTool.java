package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class SEOTool implements Tool {

    @Override
    public String getName() {
        return "seo";
    }

    @Override
    public String getDescription() {
        return "SEO关键词分析工具，分析给定商品关键词的搜索量、竞争度和相关长尾词建议。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> keywordProp = new LinkedHashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "要分析的主关键词");
        props.put("keyword", keywordProp);

        Map<String, Object> marketProp = new LinkedHashMap<>();
        marketProp.put("type", "string");
        marketProp.put("description", "目标市场/国家，如US、UK、JP");
        props.put("market", marketProp);

        Map<String, Object> categoryProp = new LinkedHashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("description", "商品品类");
        props.put("category", categoryProp);

        schema.put("properties", props);
        schema.put("required", List.of("keyword"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String keyword = (String) params.getOrDefault("keyword", "");
            String market = (String) params.getOrDefault("market", "US");
            String category = (String) params.getOrDefault("category", "");

            return analyzeKeyword(keyword, market, category);
        });
    }

    private String analyzeKeyword(String keyword, String market, String category) {
        StringBuilder sb = new StringBuilder("📊 SEO关键词分析报告\n\n");
        sb.append("主关键词: ").append(keyword).append("\n");
        sb.append("目标市场: ").append(market).append("\n");

        if (!category.isBlank()) {
            sb.append("品类: ").append(category).append("\n");
        }
        sb.append("\n");

        sb.append("## 关键词评估\n");
        int score = keyword.length() * 3 % 100;
        sb.append("- 搜索量预估: ").append(score > 70 ? "高" : score > 40 ? "中" : "低").append("\n");
        sb.append("- 竞争度: ").append(score > 60 ? "高" : score > 30 ? "中" : "低").append("\n");
        sb.append("- 推荐度: ").append("⭐⭐⭐").append("\n\n");

        sb.append("## 长尾关键词建议\n");
        String[] longTailPrefixes = {"best", "cheap", "buy", "top", "new", "premium", "wholesale", "discount"};
        String[] longTailSuffixes = {"for sale", "online", "review", "price", "near me", "2024", "for men", "for women"};
        Random random = new Random(keyword.hashCode());
        for (int i = 0; i < 8; i++) {
            String prefix = longTailPrefixes[random.nextInt(longTailPrefixes.length)];
            String suffix = longTailSuffixes[random.nextInt(longTailSuffixes.length)];
            sb.append("- ").append(prefix).append(" ").append(keyword).append(" ").append(suffix).append("\n");
        }

        sb.append("\n## 电商平台关键词建议\n");
        sb.append("- Amazon: ").append(keyword).append(", ").append(keyword).append(" for sale, buy ").append(keyword).append("\n");
        sb.append("- TikTok: #").append(keyword.replaceAll("\\s+", "")).append(", #viral, #").append(market).append("shopping\n");
        sb.append("- Google Shopping: ").append(keyword).append(" price, compare ").append(keyword).append("\n\n");

        sb.append("## 优化建议\n");
        sb.append("1. 在标题中前置核心关键词\n");
        sb.append("2. 在五点描述中自然融入长尾关键词\n");
        sb.append("3. 后台搜索词字段填写拼写变体和同义词\n");
        sb.append("4. 定期监控关键词排名变化\n");
        sb.append("5. 利用竞品关键词反向挖掘\n");

        return sb.toString();
    }
}
