package com.ecommerce.agent.rag;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.model.KnowledgeDocument;
import com.ecommerce.agent.model.Product;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import com.ecommerce.agent.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索引擎 — 整合混合搜索 + Prompt 增强
 *
 * 架构：
 * - 混合检索：BM25 关键词 + 向量语义 → 融合排序
 * - 分类型检索：支持按 source 过滤（products / knowledge / user_upload）
 * - 上下文组装：按模板拼接检索结果到 LLM Prompt
 */
@Slf4j
@Service
public class RAGService {

    private final HybridSearchService hybridSearch;
    private final AIConfig aiConfig;
    private final KnowledgeBaseLoader kbLoader;
    private final KnowledgeDocumentRepository knowledgeDocRepo;
    private final ProductRepository productRepo;

    public RAGService(HybridSearchService hybridSearch,
                      AIConfig aiConfig,
                      KnowledgeBaseLoader kbLoader,
                      KnowledgeDocumentRepository knowledgeDocRepo,
                      ProductRepository productRepo) {
        this.hybridSearch = hybridSearch;
        this.aiConfig = aiConfig;
        this.kbLoader = kbLoader;
        this.knowledgeDocRepo = knowledgeDocRepo;
        this.productRepo = productRepo;
    }

    /**
     * 为 Prompt 增强上下文（全类型混合检索）
     */
    public String augmentPrompt(String userQuery) {
        if (!aiConfig.getRag().isAugmentPrompt()) {
            return null;
        }
        // 懒加载：首次查询时按需构建向量索引
        kbLoader.ensureLoaded();

        String context = retrieveContext(userQuery);
        if (context == null || context.isBlank()) {
            return null;
        }

        return String.format("""
                以下是与用户问题相关的知识库内容，请在回答时参考这些信息：
                
                ---知识库内容---
                %s
                ---知识库内容结束---
                
                用户问题: %s
                """, context, userQuery);
    }

    /**
     * 检索上下文（全类型）
     */
    public String retrieveContext(String query) {
        return retrieveContext(query, aiConfig.getRag().getMaxResults());
    }

    public String retrieveContext(String query, int maxResults) {
        return retrieveContextWithSource(query, maxResults, null);
    }

    /**
     * 带来源过滤的检索
     * @param sourceFilter "products" / "knowledge" / "user_upload" / null（全部）
     */
    public String retrieveContextWithSource(String query, int maxResults, String sourceFilter) {
        try {
            List<HybridSearchService.SearchResult> results = hybridSearch.hybridSearch(
                    query, maxResults, aiConfig.getRag().getMinScore(), sourceFilter);

            if (results.isEmpty()) {
                log.debug("RAG检索: 未找到相关片段 (query={}, source={})", truncate(query, 50), sourceFilter);
                return null;
            }

            String context = results.stream()
                    .map(r -> "[来源: " + r.source() + "]\n" + r.text())
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.debug("RAG检索: {} 条结果 (query={}, maxScore={:.3f}, source={})",
                    results.size(), truncate(query, 50),
                    results.get(0).score(), sourceFilter != null ? sourceFilter : "all");

            return context;
        } catch (Exception e) {
            log.warn("RAG混合检索失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 分类型检索产品信息
     */
    public String retrieveProductContext(String query) {
        return retrieveContextWithSource(query, 10, "products");
    }

    /**
     * 分类型检索知识文档
     */
    public String retrieveKnowledgeContext(String query) {
        return retrieveContextWithSource(query, 5, "knowledge");
    }

    /**
     * 分类型检索用户上传文档
     */
    public String retrieveUserDocumentContext(String query) {
        return retrieveContextWithSource(query, 5, "user_upload");
    }

    /**
     * 检索原始结果（含分数和来源信息）
     */
    public List<HybridSearchService.SearchResult> search(String query, int maxResults) {
        kbLoader.ensureLoaded();
        List<HybridSearchService.SearchResult> results;
        try {
            results = hybridSearch.hybridSearch(query, maxResults, aiConfig.getRag().getMinScore(), null);
        } catch (Exception e) {
            log.warn("RAG搜索失败，切换数据库兜底: {}", e.getMessage());
            results = List.of();
        }
        return mergeWithFallback(results, databaseFallbackSearch(query, maxResults), maxResults);
    }

    public RAGContext buildContext(String query, int maxResults) {
        if (query == null || query.isBlank() || !aiConfig.getRag().isAugmentPrompt()) {
            return RAGContext.empty();
        }
        kbLoader.ensureLoaded();
        List<HybridSearchService.SearchResult> results;
        try {
            results = hybridSearch.hybridSearch(query, maxResults, aiConfig.getRag().getMinScore(), null);
        } catch (Exception e) {
            log.warn("RAG上下文构建失败: {}", e.getMessage());
            results = List.of();
        }
        if (results == null) {
            results = List.of();
        }
        results = mergeWithFallback(results, databaseFallbackSearch(query, maxResults), maxResults);
        if (results.isEmpty()) {
            return RAGContext.empty();
        }

        String context = results.stream()
                .map(r -> "[来源: " + r.source() + " | score=" + String.format("%.3f", r.score()) + "]\n" + r.text())
                .collect(Collectors.joining("\n\n---\n\n"));

        List<Map<String, Object>> citations = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            HybridSearchService.SearchResult r = results.get(i);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("rank", i + 1);
            citation.put("source", r.source());
            citation.put("score", Math.round(r.score() * 1000.0) / 1000.0);
            citation.put("snippet", truncate(r.text().replaceAll("\\s+", " "), 220));
            citations.add(citation);
        }

        return new RAGContext(context, citations);
    }

    public boolean isAvailable() {
        return aiConfig.getRag().isAugmentPrompt();
    }

    public String buildAugmentedSystemPrompt(String baseSystemPrompt, String query) {
        kbLoader.ensureLoaded();
        String context = retrieveContext(query);
        if (context == null || context.isBlank()) {
            return baseSystemPrompt;
        }

        return baseSystemPrompt + "\n\n## 参考知识库内容\n" + context
                + "\n\n请参考以上知识库内容，提供更准确、专业的回复。";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private List<HybridSearchService.SearchResult> databaseFallbackSearch(String query, int maxResults) {
        List<String> tokens = expandQueryTokens(query);
        if (tokens.isEmpty()) return List.of();

        Map<String, HybridSearchService.SearchResult> results = new LinkedHashMap<>();
        for (String token : tokens.stream().limit(6).toList()) {
            try {
                knowledgeDocRepo.findByKeyword(token, PageRequest.of(0, Math.max(2, maxResults)))
                        .forEach(doc -> addDocResult(results, doc, tokens));
            } catch (Exception e) {
                log.debug("知识文档兜底检索失败 token={}: {}", token, e.getMessage());
            }
            try {
                productRepo.searchEnabledProducts(token)
                        .stream()
                        .limit(Math.max(2, maxResults))
                        .forEach(product -> addProductResult(results, product, tokens));
            } catch (Exception e) {
                log.debug("产品兜底检索失败 token={}: {}", token, e.getMessage());
            }
        }

        return results.values().stream()
                .sorted(Comparator.comparingDouble(HybridSearchService.SearchResult::score).reversed())
                .limit(maxResults)
                .toList();
    }

    private List<String> expandQueryTokens(String query) {
        Set<String> tokens = new LinkedHashSet<>(hybridSearch.tokenize(query));
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);

        addIfContains(tokens, q, List.of("展示架", "陈列架", "陈列盒", "货架", "促销架", "展示盒"),
                "display", "display stand", "pop display", "retail display", "cardboard display", "corrugated display");
        addIfContains(tokens, q, List.of("落地", "落地式", "独立式"),
                "floor", "floor display", "floor display stand", "standing display", "fsdu");
        addIfContains(tokens, q, List.of("台面", "柜台", "桌面"),
                "counter", "countertop", "counter top", "counter display", "cdu");
        addIfContains(tokens, q, List.of("托盘", "pdq", "快速陈列"),
                "pallet", "pdq", "pallet display");
        addIfContains(tokens, q, List.of("堆头", "堆头箱", "散装"),
                "dump bin", "dumpbin", "bin display");
        addIfContains(tokens, q, List.of("瓦楞", "纸板", "纸质", "环保", "可回收"),
                "corrugated", "cardboard", "paper", "recyclable", "eco friendly");
        addIfContains(tokens, q, List.of("挂钩", "挂件"),
                "hook", "peg", "hanger");
        addIfContains(tokens, q, List.of("多层", "层架"),
                "shelf", "shelves", "rack");
        addIfContains(tokens, q, List.of("食品", "零食", "饮料"),
                "food", "snack", "beverage", "drink");
        addIfContains(tokens, q, List.of("美妆", "化妆品", "护肤"),
                "cosmetic", "beauty", "makeup", "skin care");
        addIfContains(tokens, q, List.of("价格", "报价", "定价", "多少钱"),
                "price", "quotation", "cost");
        addIfContains(tokens, q, List.of("材料", "材质", "工艺"),
                "material", "cardboard", "corrugated", "printing", "lamination");
        return tokens.stream().limit(24).toList();
    }

    private void addIfContains(Set<String> tokens, String query, List<String> chineseTriggers, String... englishTerms) {
        for (String trigger : chineseTriggers) {
            if (query.contains(trigger.toLowerCase(Locale.ROOT))) {
                tokens.addAll(List.of(englishTerms));
                return;
            }
        }
    }

    private List<HybridSearchService.SearchResult> mergeWithFallback(List<HybridSearchService.SearchResult> primary,
                                                                     List<HybridSearchService.SearchResult> fallback,
                                                                     int maxResults) {
        Map<String, HybridSearchService.SearchResult> merged = new LinkedHashMap<>();
        for (HybridSearchService.SearchResult result : primary) {
            merged.put(result.text(), result);
        }
        for (HybridSearchService.SearchResult result : fallback) {
            merged.merge(result.text(), result,
                    (oldValue, newValue) -> oldValue.score() >= newValue.score() ? oldValue : newValue);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(HybridSearchService.SearchResult::score).reversed())
                .limit(maxResults)
                .toList();
    }

    private void addDocResult(Map<String, HybridSearchService.SearchResult> results,
                              KnowledgeDocument doc,
                              List<String> tokens) {
        if (doc == null || !doc.isEnabled()) return;
        String text = "## " + doc.getTitle() + "\n\n" + truncate(doc.getContent(), 900);
        String source = "USER_UPLOAD".equals(doc.getSourceType()) ? "user_upload" : "knowledge";
        results.putIfAbsent("doc:" + doc.getId(),
                new HybridSearchService.SearchResult(text, keywordScore(text, tokens), source));
    }

    private void addProductResult(Map<String, HybridSearchService.SearchResult> results,
                                  Product product,
                                  List<String> tokens) {
        if (product == null || !product.isEnabled()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(product.getName()).append("\n");
        if (product.getSku() != null && !product.getSku().isBlank()) sb.append("SKU: ").append(product.getSku()).append("\n");
        if (product.getPrice() != null && !product.getPrice().isBlank()) sb.append("价格: ").append(product.getPrice()).append("\n");
        if (product.getCategory() != null && !product.getCategory().isBlank()) sb.append("分类: ").append(product.getCategory()).append("\n");
        if (product.getUrl() != null && !product.getUrl().isBlank()) sb.append("链接: ").append(product.getUrl()).append("\n");
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append("\n").append(truncate(product.getDescription(), 800));
        }
        String text = sb.toString();
        results.putIfAbsent("product:" + product.getId(),
                new HybridSearchService.SearchResult(text, keywordScore(text, tokens), "products"));
    }

    private double keywordScore(String text, List<String> tokens) {
        String lower = text == null ? "" : text.toLowerCase();
        double score = 0.35;
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase())) {
                score += 0.08;
            }
        }
        return Math.min(0.92, score);
    }

    public record RAGContext(String context, List<Map<String, Object>> citations) {
        public static RAGContext empty() {
            return new RAGContext(null, List.of());
        }

        public boolean hasContext() {
            return context != null && !context.isBlank() && citations != null && !citations.isEmpty();
        }
    }
}
