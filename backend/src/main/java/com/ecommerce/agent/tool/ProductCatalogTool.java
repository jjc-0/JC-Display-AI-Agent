package com.ecommerce.agent.tool;

import com.ecommerce.agent.model.Product;
import com.ecommerce.agent.rag.RAGService;
import com.ecommerce.agent.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ProductCatalogTool implements Tool {

    private final ProductRepository productRepo;
    private final RAGService ragService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductCatalogTool(ProductRepository productRepo, RAGService ragService) {
        this.productRepo = productRepo;
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "product_catalog_search";
    }

    @Override
    public String getCategory() {
        return "INFO";
    }

    @Override
    public long getTimeoutMs() {
        return 20000;
    }

    @Override
    public String getDescription() {
        return "查询公司产品库/RAG 产品库。用户询问本公司产品、产品推荐、价格、材料、SKU、分类、产品详情或产品网址时必须调用。" +
                "返回结果一定包含产品链接字段；回答用户时必须附上产品网址，不能只给产品名称。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "用户的产品查询词，可以是中文或英文，例如：台面展示架、可回收纸展示架、counter display");
        props.put("query", queryProp);

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "number");
        limitProp.put("description", "返回产品数量，默认 5，最多 10");
        props.put("limit", limitProp);

        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = String.valueOf(params.getOrDefault("query", "")).trim();
                int limit = resolveLimit(params.get("limit"));
                if (query.isBlank()) {
                    return "{\"error\":\"缺少 query 参数\"}";
                }

                Map<Long, Product> matched = new LinkedHashMap<>();
                List<String> tokens = expandTokens(query);
                for (String token : tokens) {
                    productRepo.searchEnabledProducts(token).stream()
                            .limit(limit)
                            .forEach(p -> {
                                if (p.getId() != null) matched.putIfAbsent(p.getId(), p);
                            });
                    if (matched.size() >= limit) break;
                }

                List<Map<String, Object>> products = matched.values().stream()
                        .limit(limit)
                        .map(this::toProductMap)
                        .toList();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("query", query);
                result.put("total", products.size());
                result.put("products", products);
                result.put("rag_context", ragService.retrieveProductContext(query));
                result.put("answer_rule", "回答产品信息时必须列出每个产品的 Product URL/产品链接。价格、材料、SKU、分类有记录就引用；没有记录时明确说明需按规格、数量和贸易条款确认。");

                return objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                log.error("产品库工具查询失败", e);
                return "{\"error\":\"产品库查询失败: " + e.getMessage() + "\"}";
            }
        });
    }

    private int resolveLimit(Object value) {
        if (value instanceof Number n) {
            return Math.max(1, Math.min(10, n.intValue()));
        }
        return 5;
    }

    private List<String> expandTokens(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        tokens.add(query);
        addIfContains(tokens, q, List.of("展示架", "陈列架", "展示盒", "陈列盒"), "display", "display stand", "cardboard display", "pop display");
        addIfContains(tokens, q, List.of("台面", "柜台", "桌面"), "counter", "countertop", "counter display", "cdu");
        addIfContains(tokens, q, List.of("落地", "落地式"), "floor", "floor display", "floor display stand", "fsdu");
        addIfContains(tokens, q, List.of("托盘", "pdq"), "pallet", "pdq", "pallet display");
        addIfContains(tokens, q, List.of("堆头", "堆头箱"), "dump bin", "dumpbin", "bin display");
        addIfContains(tokens, q, List.of("瓦楞", "纸板", "纸质", "环保", "可回收"), "corrugated", "cardboard", "paper", "recyclable");
        addIfContains(tokens, q, List.of("食品", "零食", "饮料"), "food", "snack", "beverage", "drink");
        addIfContains(tokens, q, List.of("美妆", "化妆品", "护肤"), "cosmetic", "beauty", "makeup", "skin care");
        return tokens.stream().distinct().limit(20).toList();
    }

    private void addIfContains(List<String> tokens, String query, List<String> triggers, String... englishTerms) {
        for (String trigger : triggers) {
            if (query.contains(trigger.toLowerCase(Locale.ROOT))) {
                tokens.addAll(List.of(englishTerms));
                return;
            }
        }
    }

    private Map<String, Object> toProductMap(Product product) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", product.getId());
        item.put("name", product.getName());
        item.put("sku", product.getSku() != null ? product.getSku() : "");
        item.put("price", product.getPrice() != null ? product.getPrice() : "");
        item.put("category", product.getCategory() != null ? product.getCategory() : "");
        item.put("url", product.getUrl() != null ? product.getUrl() : "");
        item.put("imageUrl", product.getImageUrl() != null ? product.getImageUrl() : "");
        item.put("description", product.getDescription() != null
                ? product.getDescription().substring(0, Math.min(500, product.getDescription().length()))
                : "");
        return item;
    }
}
