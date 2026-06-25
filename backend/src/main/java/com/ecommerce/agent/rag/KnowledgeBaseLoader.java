package com.ecommerce.agent.rag;

import com.ecommerce.agent.model.KnowledgeDocument;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import com.ecommerce.agent.repository.ProductRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库加载器 — 从 MySQL 加载数据 → 智能分块 → 向量索引
 *
 * 数据来源：
 * 1. knowledge_documents 表（内置种子文档 + 用户上传文档）
 * 2. products 表（爬取的官网产品数据）
 *
 * 分块策略：
 * - 产品数据：每产品一个 chunk，标记 source=products
 * - 知识文档：SmartChunker 按标题/段落拆分，标记 source=knowledge 或 user_upload
 */
@Slf4j
@Component
public class KnowledgeBaseLoader {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SmartChunker smartChunker;
    private final HybridSearchService hybridSearch;
    private final KnowledgeDocumentRepository knowledgeDocRepo;
    private final ProductRepository productRepo;

    public KnowledgeBaseLoader(EmbeddingStore<TextSegment> embeddingStore,
                               EmbeddingModel embeddingModel,
                               SmartChunker smartChunker,
                               HybridSearchService hybridSearch,
                               KnowledgeDocumentRepository knowledgeDocRepo,
                               ProductRepository productRepo) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.smartChunker = smartChunker;
        this.hybridSearch = hybridSearch;
        this.knowledgeDocRepo = knowledgeDocRepo;
        this.productRepo = productRepo;
    }

    private final AtomicBoolean loaded = new AtomicBoolean(false);

    /**
     * 仅执行文档种子写入 MySQL（不调用任何 Embedding API）
     * doLoad() 改为懒加载：首次 RAG 查询时触发，避免每次启动重复嵌入
     */
    @PostConstruct
    public void init() {
        seedDefaultDocuments();
    }

    /** 懒加载入口 — 首次查询时自动触发，后续调用直接跳过 */
    public void ensureLoaded() {
        if (loaded.get()) return;
        synchronized (loaded) {
            if (loaded.get()) return;
            try {
                doLoad();
                loaded.set(true);
            } catch (Exception e) {
                log.error("知识库向量索引构建失败", e);
            }
        }
    }

    /**
     * 首次启动时将硬编码知识文档写入 MySQL
     */
    public void seedDefaultDocuments() {
        if (knowledgeDocRepo.count() > 0) {
            log.info("知识库文档已存在于 MySQL，跳过初始化");
            return;
        }
        log.info("首次启动，初始化知识库文档到 MySQL...");
        List<Document> docs = KnowledgeDocuments.getAllDocuments();
        for (Document doc : docs) {
            String title = extractTitle(doc.text());
            String category = guessCategory(title);
            knowledgeDocRepo.save(KnowledgeDocument.builder()
                    .title(title)
                    .content(doc.text())
                    .category(category)
                    .sourceType("BUILT_IN")
                    .fileType("MARKDOWN")
                    .enabled(true)
                    .build());
        }
        log.info("已写入 {} 篇文档到 MySQL", docs.size());
    }

    /**
     * 强制重新加载知识库（含产品数据）— 由爬虫完成后或文档上传后调用
     */
    public void forceReload() {
        loaded.set(false);
        hybridSearch.clearCache();
        doLoad();
        loaded.set(true);
    }

    private void doLoad() {
        log.info("开始构建知识库向量索引...");
        long start = System.currentTimeMillis();

        List<TextSegment> allSegments = new ArrayList<>();

        // 1. 加载已启用的知识文档（内置 + 用户上传）
        List<KnowledgeDocument> knowledgeDocs = knowledgeDocRepo.findByEnabledTrueOrderByTitleAsc();
        for (KnowledgeDocument kd : knowledgeDocs) {
            String source = "USER_UPLOAD".equals(kd.getSourceType()) ? "user_upload" : "knowledge";
            Document doc = Document.from(kd.getTitle() + "\n\n" + kd.getContent());
            allSegments.addAll(smartChunker.chunk(List.of(doc), source));
        }
        log.info("从 MySQL 加载 {} 篇知识文档 (knowledge + user_upload)", knowledgeDocs.size());

        // 2. 加载产品数据 — 每产品一个 chunk
        List<com.ecommerce.agent.model.Product> products = productRepo.findByEnabledTrueOrderByNameAsc();
        for (com.ecommerce.agent.model.Product p : products) {
            String md = toProductMarkdown(p);
            // 每个产品作为一个段落，不做进一步拆分（性能高，上下文完整）
            allSegments.add(TextSegment.from(md,
                    new dev.langchain4j.data.document.Metadata().put("source", "products")));
        }
        log.info("从 MySQL 加载 {} 个产品", products.size());

        // 3. 后备：如果数据库为空，用硬编码数据
        if (allSegments.isEmpty()) {
            log.warn("MySQL 中无数据，使用硬编码文档作为后备");
            List<Document> fallbackDocs = new ArrayList<>(KnowledgeDocuments.getAllDocuments());
            allSegments.addAll(smartChunker.chunk(fallbackDocs, "knowledge"));
        }

        // 4. 向量化 & 写入 EmbeddingStore — 仅向量化知识文档
        // 产品数据(866条)不纳入向量索引：量大烧钱、且产品搜索走 /api/agent/knowledge/products 接口即可
        int embeddedCount = 0;
        for (TextSegment segment : allSegments) {
            String source = segment.metadata() != null ? segment.metadata().getString("source") : null;
            if ("products".equals(source)) {
                continue; // 跳过产品，不调用Embedding API
            }
            try {
                var embedding = embeddingModel.embed(segment.text());
                if (embedding != null && embedding.content() != null) {
                    embeddingStore.add(embedding.content(), segment);
                    embeddedCount++;
                }
            } catch (Exception e) {
                log.warn("向量化失败 (source={}): {}", source, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("知识库向量索引构建完成: {} 知识片段向量化 + {} 产品跳过, 耗时 {}ms",
                embeddedCount, products.size(), elapsed);
    }

    // ---- 辅助方法 ----

    private String extractTitle(String text) {
        if (text == null || text.isBlank()) return "Untitled";
        String trimmed = text.trim();
        int newline = trimmed.indexOf('\n');
        String firstLine = newline > 0 ? trimmed.substring(0, newline) : trimmed.substring(0, Math.min(80, trimmed.length()));
        return firstLine.replaceAll("^#+\\s*", "").trim();
    }

    private String guessCategory(String title) {
        String t = title.toLowerCase();
        if (t.contains("公司")) return "company";
        if (t.contains("产品") || t.contains("规格")) return "product";
        if (t.contains("市场")) return "market";
        if (t.contains("法规") || t.contains("认证") || t.contains("合规")) return "compliance";
        if (t.contains("物流") || t.contains("运输")) return "logistics";
        if (t.contains("展会") || t.contains("展览")) return "trade_show";
        if (t.contains("术语")) return "terminology";
        if (t.contains("平台") || t.contains("b2b")) return "platform";
        if (t.contains("邮件") || t.contains("询盘")) return "email";
        if (t.contains("本地") || t.contains("化指南")) return "localization";
        return "general";
    }

    private String toProductMarkdown(com.ecommerce.agent.model.Product p) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(p.getName() != null ? p.getName() : "未知产品").append("\n\n");
        if (p.getSku() != null && !p.getSku().isBlank()) {
            sb.append("- **型号/SKU**: ").append(p.getSku()).append("\n");
        }
        if (p.getPrice() != null && !p.getPrice().isBlank()) {
            sb.append("- **价格**: ").append(p.getPrice()).append("\n");
        }
        if (p.getCategory() != null && !p.getCategory().isBlank()) {
            sb.append("- **分类**: ").append(p.getCategory()).append("\n");
        }
        if (p.getUrl() != null && !p.getUrl().isBlank()) {
            sb.append("- **产品链接**: ").append(p.getUrl()).append("\n");
        }
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            sb.append("\n**产品描述**:\n").append(p.getDescription()).append("\n");
        }
        sb.append("\n---\n");
        return sb.toString();
    }
}
