package com.ecommerce.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 混合检索引擎 — BM25 关键词 + 向量语义检索
 *
 * 策略：
 * 1. 向量检索（语义相似度）— 捕获意图接近但措辞不同的内容
 * 2. BM25 关键词匹配 — 精确匹配专业术语、产品型号、SKU 等
 * 3. 分数归一化 + 加权融合 → 最终排序
 *
 * 优势：覆盖面广（语义+关键词互补），准确性高（双路互验），
 *       避免了纯向量检索在专业术语上的「失配」问题
 */
@Slf4j
@Service
public class HybridSearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // BM25 参数
    private static final double K1 = 1.5;  // 词频饱和度
    private static final double B = 0.75;  // 文档长度归一化

    // 融合权重：向量权重 vs BM25 权重
    private static final double VECTOR_WEIGHT = 0.5;
    private static final double BM25_WEIGHT = 0.5;

    // chunk → 分词缓存
    private final Map<String, List<String>> tokenCache = new ConcurrentHashMap<>();

    public HybridSearchService(EmbeddingStore<TextSegment> embeddingStore,
                               EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 混合搜索主入口
     * @param query         用户查询
     * @param maxResults    最大返回数
     * @param minScore      最低相似度阈值
     * @param sourceFilter  来源过滤（null = 不限），如 "products", "knowledge", "user_upload"
     * @return 融合排序后的文档片段列表
     */
    public List<SearchResult> hybridSearch(String query, int maxResults, double minScore, String sourceFilter) {
        // 只计算一次 query embedding，供向量检索和 BM25 复用
        Embedding queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query).content();
        } catch (Exception e) {
            log.warn("查询向量化失败: {}", e.getMessage());
            return List.of();
        }

        // 路径1: 向量语义检索
        List<ScoredChunk> vectorResults = vectorSearchEmbedding(queryEmbedding, maxResults * 2, minScore);
        log.debug("向量检索: {} 条结果", vectorResults.size());

        // 路径2: BM25 关键词检索（复用同一个 embedding）
        List<ScoredChunk> bm25Results = bm25SearchEmbedding(queryEmbedding, query, maxResults * 2);
        log.debug("BM25检索: {} 条结果", bm25Results.size());

        // 融合排序
        List<SearchResult> merged = mergeResults(vectorResults, bm25Results, maxResults, sourceFilter);
        log.debug("融合后: {} 条结果 (sourceFilter={})", merged.size(), sourceFilter);
        return merged;
    }

    /**
     * 向量语义检索（使用预计算 embedding，避免重复调用 API）
     */
    private List<ScoredChunk> vectorSearchEmbedding(Embedding queryEmbedding, int maxResults, double minScore) {
        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

            return matches.stream()
                    .map(m -> new ScoredChunk(normalizeScore(m.score()), m.embedded().text(),
                            m.embedded().metadata() != null ?
                                    m.embedded().metadata().getString("source") : null))
                    .toList();
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * BM25 关键词检索 — 复用预计算 embedding 作为候选集
     */
    private List<ScoredChunk> bm25SearchEmbedding(Embedding queryEmbedding, String query, int maxResults) {
        // 分词查询
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        // 复用预计算的 embedding 获取候选集（不再重复调用 API）
        List<ScoredChunk> candidates;
        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(500)
                    .minScore(0.25)
                    .build();
            candidates = embeddingStore.search(request).matches().stream()
                    .map(m -> new ScoredChunk(0, m.embedded().text(),
                            m.embedded().metadata() != null ?
                                    m.embedded().metadata().getString("source") : null))
                    .toList();
        } catch (Exception e) {
            log.warn("BM25候选集获取失败: {}", e.getMessage());
            return List.of();
        }

        // 计算平均文档长度
        double avgDocLen = candidates.stream()
                .mapToInt(c -> c.text().length())
                .average()
                .orElse(500);

        // 对每个候选计算 BM25 分数
        List<ScoredChunk> scored = new ArrayList<>();
        for (ScoredChunk chunk : candidates) {
            List<String> docTokens = getCachedTokens(chunk.text());
            double bm25Score = computeBM25(queryTokens, docTokens, avgDocLen);
            if (bm25Score > 0) {
                scored.add(new ScoredChunk(normalizeScore(bm25Score), chunk.text(), chunk.source()));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.subList(0, Math.min(maxResults, scored.size()));
    }

    /**
     * BM25 分数计算
     */
    private double computeBM25(List<String> queryTokens, List<String> docTokens, double avgDocLen) {
        int docLen = docTokens.size();
        double score = 0;
        for (String qt : queryTokens) {
            int tf = countOccurrences(docTokens, qt);
            if (tf == 0) continue;
            // 简化 IDF (未建全局索引，使用固定常数)
            double idf = 1.5;
            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * (1 - B + B * docLen / avgDocLen);
            score += idf * numerator / denominator;
        }
        return score;
    }

    private int countOccurrences(List<String> tokens, String target) {
        int count = 0;
        for (String t : tokens) {
            if (t.equals(target)) count++;
        }
        return count;
    }

    /**
     * 结果融合 — Reciprocal Rank Fusion + 加权
     */
    private List<SearchResult> mergeResults(List<ScoredChunk> vectorResults,
                                            List<ScoredChunk> bm25Results,
                                            int maxResults,
                                            String sourceFilter) {
        // chunk text → 融合分数
        Map<String, Double> fusedScores = new LinkedHashMap<>();
        Map<String, String> sourceMap = new HashMap<>();

        // RRF 常数
        final double k = 60;

        // 向量结果：RRF
        for (int i = 0; i < vectorResults.size(); i++) {
            ScoredChunk c = vectorResults.get(i);
            double rrf = VECTOR_WEIGHT * (1.0 / (k + i + 1));
            fusedScores.merge(c.text(), rrf, Double::sum);
            if (c.source() != null) sourceMap.putIfAbsent(c.text(), c.source());
        }

        // BM25 结果：RRF
        for (int i = 0; i < bm25Results.size(); i++) {
            ScoredChunk c = bm25Results.get(i);
            double rrf = BM25_WEIGHT * (1.0 / (k + i + 1));
            fusedScores.merge(c.text(), rrf, Double::sum);
            if (c.source() != null) sourceMap.putIfAbsent(c.text(), c.source());
        }

        // 排序 + 过滤 + 截断
        Stream<Map.Entry<String, Double>> stream = fusedScores.entrySet().stream();
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            stream = stream.filter(e -> sourceFilter.equals(sourceMap.get(e.getKey())));
        }

        return stream
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(e -> new SearchResult(e.getKey(), e.getValue(),
                        sourceMap.getOrDefault(e.getKey(), "unknown")))
                .toList();
    }

    /**
     * 简单分词器
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        // 按非字母数字字符分割 + 小写 + 过滤停用词
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9\\u4e00-\\u9fff]+"))
                .filter(t -> !t.isBlank() && t.length() >= 2 && !isStopWord(t))
                .toList();
    }

    private List<String> getCachedTokens(String text) {
        return tokenCache.computeIfAbsent(text, this::tokenize);
    }

    private boolean isStopWord(String token) {
        // 常见中英文停用词
        return switch (token) {
            case "the", "a", "an", "is", "are", "was", "were", "be", "been",
                 "being", "have", "has", "had", "do", "does", "did", "will",
                 "would", "could", "should", "may", "might", "can", "shall",
                 "to", "of", "in", "for", "on", "with", "at", "by", "from",
                 "and", "or", "but", "not", "no", "this", "that", "it", "its",
                 "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都",
                 "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你","会",
                 "着", "没有", "看", "好", "自己", "这" -> true;
            default -> false;
        };
    }

    /**
     * 分数归一化到 [0, 1]
     */
    private double normalizeScore(double score) {
        return Math.max(0, Math.min(1, score));
    }

    /**
     * 带分数的 chunk
     */
    public record ScoredChunk(double score, String text, String source) {}

    /**
     * 搜索结果
     */
    public record SearchResult(String text, double score, String source) {}

    /**
     * 清空 token 缓存（知识库重载时调用）
     */
    public void clearCache() {
        tokenCache.clear();
    }
}
