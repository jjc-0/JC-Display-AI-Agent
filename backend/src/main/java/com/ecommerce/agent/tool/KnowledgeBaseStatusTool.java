package com.ecommerce.agent.tool;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import com.ecommerce.agent.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 查询 RAG 产品库和知识库状态，避免模型把会话数量误认为产品数量。
 */
@Slf4j
@Component
public class KnowledgeBaseStatusTool implements Tool {

    private final ProductRepository productRepo;
    private final KnowledgeDocumentRepository knowledgeDocRepo;
    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeBaseStatusTool(ProductRepository productRepo,
                                   KnowledgeDocumentRepository knowledgeDocRepo,
                                   AIConfig aiConfig) {
        this.productRepo = productRepo;
        this.knowledgeDocRepo = knowledgeDocRepo;
        this.aiConfig = aiConfig;
    }

    @Override
    public String getName() {
        return "knowledge_base_status";
    }

    @Override
    public String getCategory() {
        return "INFO";
    }

    @Override
    public long getTimeoutMs() {
        return 10000;
    }

    @Override
    public String getDescription() {
        return "查询企业 RAG 知识库和产品库状态。只要用户询问“RAG产品库有多少产品”、" +
                "“产品库数量”、“知识库文档数量”、“产品是否已向量化”、“索引状态”等问题，必须优先调用本工具。" +
                "注意：不要用 database_query 的 sessions/会话数量回答产品库数量。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> includeTopics = new LinkedHashMap<>();
        includeTopics.put("type", "boolean");
        includeTopics.put("description", "是否返回知识文档标题列表，默认 true。");
        props.put("include_topics", includeTopics);

        schema.put("properties", props);
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean includeTopics = !Boolean.FALSE.equals(params.get("include_topics"));
                long productCount = productRepo.count();
                long enabledProductCount = productRepo.countByEnabledTrue();
                long documentCount = knowledgeDocRepo.count();
                long userUploadDocumentCount = knowledgeDocRepo.countBySourceType("USER_UPLOAD");
                long builtInDocumentCount = knowledgeDocRepo.countBySourceType("BUILT_IN");

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("answer_hint", "当前 RAG 产品库中的产品数量是 " + productCount
                        + "，其中启用产品 " + enabledProductCount + "。这不是会话数量。");
                result.put("rag_product_count", productCount);
                result.put("enabled_product_count", enabledProductCount);
                result.put("knowledge_document_count", documentCount);
                result.put("built_in_document_count", builtInDocumentCount);
                result.put("user_upload_document_count", userUploadDocumentCount);
                result.put("auto_inject_enabled", aiConfig.getRag().isAugmentPrompt());
                result.put("product_vector_index_enabled", aiConfig.getRag().isIndexProducts());
                result.put("product_embedding_scope", aiConfig.getRag().getMaxProductEmbeddings() == 0 ? "all" : "limited");
                result.put("max_product_embeddings", aiConfig.getRag().getMaxProductEmbeddings());
                result.put("embedding_index_persisted", Files.exists(embeddingIndexPath()));
                result.put("embedding_manifest_persisted", Files.exists(embeddingManifestPath()));
                result.put("retrieval_mode", aiConfig.getRag().isIndexProducts()
                        ? "知识文档 + 产品库向量检索，中文查询扩展，中英关键词兜底"
                        : "知识文档向量检索 + MySQL 关键词兜底");

                if (includeTopics) {
                    List<String> topics = knowledgeDocRepo.findByEnabledTrueOrderByTitleAsc()
                            .stream()
                            .map(doc -> doc.getTitle())
                            .toList();
                    result.put("topics", topics);
                }

                return objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                log.error("查询知识库状态失败", e);
                return "{\"error\":\"查询知识库状态失败: " + e.getMessage() + "\"}";
            }
        });
    }

    private Path embeddingIndexPath() {
        String configured = aiConfig.getRag().getEmbeddingIndexPath();
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of(System.getProperty("user.home"), ".jc-agent", "rag", "embedding-index.json");
    }

    private Path embeddingManifestPath() {
        String configured = aiConfig.getRag().getEmbeddingManifestPath();
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of(System.getProperty("user.home"), ".jc-agent", "rag", "embedding-manifest.json");
    }
}
