package com.ecommerce.agent.rag;

import com.ecommerce.agent.config.AIConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final EmbeddingModel embeddingModel;
    private final AIConfig aiConfig;

    public RAGService(HybridSearchService hybridSearch,
                      EmbeddingModel embeddingModel,
                      AIConfig aiConfig) {
        this.hybridSearch = hybridSearch;
        this.embeddingModel = embeddingModel;
        this.aiConfig = aiConfig;
    }

    /**
     * 为 Prompt 增强上下文（全类型混合检索）
     */
    public String augmentPrompt(String userQuery) {
        if (!aiConfig.getRag().isAugmentPrompt()) {
            return null;
        }

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
        return hybridSearch.hybridSearch(query, maxResults, aiConfig.getRag().getMinScore(), null);
    }

    private volatile Boolean availabilityCache = null;

    public boolean isAvailable() {
        if (availabilityCache != null) {
            return availabilityCache;
        }
        try {
            embeddingModel.embed("test");
            availabilityCache = true;
        } catch (Exception e) {
            log.warn("RAG嵌入模型不可用: {}", e.getMessage());
            availabilityCache = false;
        }
        return availabilityCache;
    }

    public String buildAugmentedSystemPrompt(String baseSystemPrompt, String query) {
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
}
