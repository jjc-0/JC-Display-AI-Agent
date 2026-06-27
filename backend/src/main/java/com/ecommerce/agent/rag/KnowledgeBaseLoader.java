package com.ecommerce.agent.rag;

import com.ecommerce.agent.config.AIConfig;
import com.ecommerce.agent.model.KnowledgeDocument;
import com.ecommerce.agent.model.Product;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import com.ecommerce.agent.repository.ProductRepository;
import com.ecommerce.agent.service.RagIndexProgressService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SmartChunker smartChunker;
    private final HybridSearchService hybridSearch;
    private final KnowledgeDocumentRepository knowledgeDocRepo;
    private final ProductRepository productRepo;
    private final AIConfig aiConfig;
    private final RagIndexProgressService indexProgressService;

    public KnowledgeBaseLoader(EmbeddingStore<TextSegment> embeddingStore,
                               EmbeddingModel embeddingModel,
                               SmartChunker smartChunker,
                               HybridSearchService hybridSearch,
                               KnowledgeDocumentRepository knowledgeDocRepo,
                               ProductRepository productRepo,
                               AIConfig aiConfig,
                               RagIndexProgressService indexProgressService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.smartChunker = smartChunker;
        this.hybridSearch = hybridSearch;
        this.knowledgeDocRepo = knowledgeDocRepo;
        this.productRepo = productRepo;
        this.aiConfig = aiConfig;
        this.indexProgressService = indexProgressService;
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
        if (indexProgressService.beginIfIdle("sync")) {
            try {
                forceReloadInternal();
                indexProgressService.complete("索引更新完成");
            } catch (Exception e) {
                indexProgressService.fail("索引更新失败: " + e.getMessage());
                throw e;
            }
            return;
        }
        log.info("已有 RAG 索引更新任务正在运行，跳过本次重复触发");
    }

    public Map<String, Object> startAsyncReload(String trigger) {
        return indexProgressService.start(trigger, this::forceReloadInternal);
    }

    public Map<String, Object> getIndexProgress() {
        return indexProgressService.snapshot();
    }

    private void forceReloadInternal() {
        loaded.set(false);
        hybridSearch.clearCache();
        doLoad();
        loaded.set(true);
    }

    private void doLoad() {
        log.info("开始构建知识库向量索引...");
        long start = System.currentTimeMillis();
        indexProgressService.update("loading", 5, 0, 0, "正在读取知识文档和产品数据");

        List<TextSegment> knowledgeSegments = new ArrayList<>();
        List<TextSegment> productSegments = new ArrayList<>();

        // 1. 加载已启用的知识文档（内置 + 用户上传）
        List<KnowledgeDocument> knowledgeDocs = knowledgeDocRepo.findByEnabledTrueOrderByTitleAsc();
        for (KnowledgeDocument kd : knowledgeDocs) {
            String source = "USER_UPLOAD".equals(kd.getSourceType()) ? "user_upload" : "knowledge";
            Document doc = Document.from(kd.getTitle() + "\n\n" + kd.getContent());
            List<TextSegment> chunks = smartChunker.chunk(List.of(doc), source);
            for (int i = 0; i < chunks.size(); i++) {
                TextSegment chunk = chunks.get(i);
                String indexKey = "doc:" + kd.getId() + ":" + i;
                Metadata metadata = chunk.metadata() != null ? chunk.metadata().copy() : new Metadata();
                metadata.put("source", source);
                metadata.put("indexKey", indexKey);
                metadata.put("docId", kd.getId() != null ? String.valueOf(kd.getId()) : "");
                metadata.put("docTitle", kd.getTitle() != null ? kd.getTitle() : "");
                knowledgeSegments.add(TextSegment.from(chunk.text(), metadata));
            }
        }
        log.info("从 MySQL 加载 {} 篇知识文档 (knowledge + user_upload)", knowledgeDocs.size());
        indexProgressService.update("loading_documents", 12, knowledgeSegments.size(), 0,
                "已读取 " + knowledgeDocs.size() + " 篇知识文档");

        // 2. 加载产品数据 — 每产品一个 chunk
        List<Product> products = productRepo.findByEnabledTrueOrderByNameAsc();
        for (Product p : products) {
            String md = toProductMarkdown(p);
            Metadata metadata = new Metadata()
                    .put("source", "products")
                    .put("indexKey", "product:" + p.getId())
                    .put("productId", p.getId() != null ? String.valueOf(p.getId()) : "")
                    .put("productName", p.getName() != null ? p.getName() : "");
            productSegments.add(TextSegment.from(md, metadata));
        }
        log.info("从 MySQL 加载 {} 个产品", products.size());
        indexProgressService.update("loading_products", 22, productSegments.size(), 0,
                "已读取 " + products.size() + " 个启用产品");

        // 3. 后备：如果数据库为空，用硬编码数据
        if (knowledgeSegments.isEmpty() && productSegments.isEmpty()) {
            log.warn("MySQL 中无数据，使用硬编码文档作为后备");
            List<Document> fallbackDocs = new ArrayList<>(KnowledgeDocuments.getAllDocuments());
            knowledgeSegments.addAll(smartChunker.chunk(fallbackDocs, "knowledge"));
        }

        List<IndexItem> targetItems = buildIndexItems(knowledgeSegments, productSegments);
        indexProgressService.update("manifest", 30, 0, targetItems.size(), "正在比对已保存的向量索引清单");
        Map<String, String> manifest = loadManifest();
        Set<String> targetKeys = new LinkedHashSet<>();
        List<IndexItem> changedItems = new ArrayList<>();
        for (IndexItem item : targetItems) {
            targetKeys.add(item.key());
            if (!item.hash().equals(manifest.get(item.key()))) {
                changedItems.add(item);
            }
        }
        Set<String> obsoleteKeys = new LinkedHashSet<>();
        for (String key : manifest.keySet()) {
            if (!targetKeys.contains(key)) {
                obsoleteKeys.add(key);
            }
        }
        indexProgressService.updateTotals(targetItems.size(), changedItems.size(), obsoleteKeys.size(),
                "发现 " + changedItems.size() + " 条需要向量化，" + obsoleteKeys.size() + " 条需要移除");

        if (changedItems.isEmpty() && obsoleteKeys.isEmpty() && isPersistedIndexPresent()) {
            long elapsed = System.currentTimeMillis() - start;
            log.info("知识库向量索引已是最新，跳过重复向量化，共 {} 条索引项，耗时 {}ms", targetItems.size(), elapsed);
            indexProgressService.update("completed", 100, targetItems.size(), targetItems.size(),
                    "索引已是最新，无需重复向量化");
            return;
        }

        Set<String> keysToRemove = new LinkedHashSet<>(obsoleteKeys);
        for (IndexItem item : changedItems) {
            keysToRemove.add(item.key());
        }
        indexProgressService.update("removing", 38, 0, targetItems.size(), "正在清理过期或已变化的向量");
        removeChangedItems(keysToRemove);
        List<IndexItem> changedKnowledgeItems = new ArrayList<>();
        List<IndexItem> changedProductItems = new ArrayList<>();
        for (IndexItem item : changedItems) {
            if ("products".equals(item.source())) {
                changedProductItems.add(item);
            } else {
                changedKnowledgeItems.add(item);
            }
        }
        int embeddedKnowledgeCount = embedItems(changedKnowledgeItems, "embedding_documents", 40, 62, targetItems.size(), 0);
        int embeddedProductCount = embedItems(changedProductItems, "embedding_products", 62, 92, targetItems.size(), embeddedKnowledgeCount);

        Map<String, String> nextManifest = new LinkedHashMap<>();
        for (IndexItem item : targetItems) {
            nextManifest.put(item.key(), item.hash());
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("知识库向量索引增量更新完成: {} 知识片段 + {} 产品片段新向量化, 总索引项 {}, 耗时 {}ms",
                embeddedKnowledgeCount, embeddedProductCount, targetItems.size(), elapsed);
        indexProgressService.update("persisting", 95, embeddedKnowledgeCount + embeddedProductCount, targetItems.size(),
                "正在保存向量索引和增量清单");
        persistIndex(nextManifest);
    }

    private int embedItems(List<IndexItem> items,
                           String phase,
                           int startProgress,
                           int endProgress,
                           int totalItems,
                           int processedOffset) {
        int embeddedCount = 0;
        int itemCount = items.size();
        if (itemCount == 0) {
            indexProgressService.update(phase, endProgress, processedOffset, totalItems, "没有新的片段需要向量化");
            return embeddedCount;
        }
        for (int i = 0; i < itemCount; i++) {
            IndexItem item = items.get(i);
            try {
                TextSegment segment = item.segment();
                var embedding = embeddingModel.embed(segment.text());
                if (embedding != null && embedding.content() != null) {
                    embeddingStore.add(embedding.content(), segment);
                    embeddedCount++;
                }
            } catch (Exception e) {
                log.warn("向量化失败 (key={}, source={}): {}", item.key(), item.source(), e.getMessage());
            }
            int progress = startProgress + (int) Math.round(((i + 1) * 1.0 / itemCount) * (endProgress - startProgress));
            indexProgressService.update(phase, progress, processedOffset + i + 1, totalItems,
                    "正在向量化 " + (i + 1) + "/" + itemCount + " 个片段");
        }
        return embeddedCount;
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

    private String toProductMarkdown(Product p) {
        StringBuilder sb = new StringBuilder();
        String name = safe(p.getName(), "未知产品");
        String description = safe(p.getDescription(), "");
        String category = safe(p.getCategory(), "");
        String aliases = buildChineseAliases(name + " " + category + " " + description);

        sb.append("## ").append(name).append("\n\n");
        sb.append("Source: 公司产品库 / Company product catalog\n");
        sb.append("Product searchable Chinese aliases: ").append(aliases).append("\n");
        sb.append("English product keywords: ").append(buildEnglishKeywords(name + " " + category + " " + description)).append("\n\n");
        if (p.getSku() != null && !p.getSku().isBlank()) {
            sb.append("- **型号/SKU**: ").append(p.getSku()).append("\n");
        }
        if (p.getPrice() != null && !p.getPrice().isBlank()) {
            sb.append("- **定价/Price**: ").append(p.getPrice()).append("\n");
        } else {
            sb.append("- **定价/Price**: 未记录，回答时请说明需根据尺寸、印刷、数量和贸易条款报价。\n");
        }
        if (!category.isBlank()) {
            sb.append("- **分类/Category**: ").append(category).append("\n");
        }
        if (p.getUrl() != null && !p.getUrl().isBlank()) {
            sb.append("- **产品链接/Product URL**: ").append(p.getUrl()).append("\n");
        }
        sb.append("- **常见材料/Materials**: 瓦楞纸板 corrugated cardboard, B-flute, E-flute, BC-flute, 300g art paper, CMYK printing, lamination. 若原始描述有更具体材料，以原始描述为准。\n");
        sb.append("- **适用场景/Applications**: 零售终端陈列、商超促销、品牌展示、食品饮料、美妆、玩具、日化、FMCG 海外市场。\n");
        sb.append("- **可定制项/Customization**: 尺寸、层数、承重、印刷图案、表面工艺、包装方式、MOQ、样品与交期。\n");
        if (!description.isBlank()) {
            sb.append("\n**产品描述/Product description**:\n").append(description).append("\n");
        }
        sb.append("\n回答规则: 当用户中文咨询本公司产品、价格、材料、规格、用途或推荐产品时，优先引用本产品库信息。若价格或材料未在原始产品中明确记录，说明需按规格和订单量确认，不要编造固定报价。\n");
        sb.append("\n---\n");
        return sb.toString();
    }

    private String buildChineseAliases(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add("展示架");
        aliases.add("纸展示架");
        aliases.add("瓦楞纸展示架");
        aliases.add("纸货架");
        aliases.add("POP陈列架");
        aliases.add("促销展示架");
        aliases.add("零售陈列道具");

        if (containsAny(lower, "floor", "standing", "fsdu", "free standing")) {
            aliases.add("落地展示架");
            aliases.add("落地式陈列架");
            aliases.add("独立式展示架");
        }
        if (containsAny(lower, "counter", "countertop", "counter top", "cdu")) {
            aliases.add("台面展示架");
            aliases.add("柜台展示盒");
            aliases.add("桌面陈列架");
        }
        if (containsAny(lower, "pallet", "pdq")) {
            aliases.add("托盘展示架");
            aliases.add("PDQ展示盒");
            aliases.add("快速陈列盒");
        }
        if (containsAny(lower, "dump bin", "dumpbin", "bin")) {
            aliases.add("堆头箱");
            aliases.add("散装陈列箱");
            aliases.add("促销堆头");
        }
        if (containsAny(lower, "cardboard", "corrugated", "paper")) {
            aliases.add("纸板");
            aliases.add("瓦楞纸板");
            aliases.add("环保纸质陈列");
        }
        if (containsAny(lower, "hook", "peg", "hanger")) {
            aliases.add("挂钩展示架");
            aliases.add("挂件陈列架");
        }
        if (containsAny(lower, "shelf", "shelves", "rack")) {
            aliases.add("货架");
            aliases.add("多层展示架");
        }
        if (containsAny(lower, "food", "snack", "drink", "beverage")) {
            aliases.add("食品饮料陈列");
            aliases.add("零食展示架");
            aliases.add("饮料促销架");
        }
        if (containsAny(lower, "cosmetic", "beauty", "makeup", "skin")) {
            aliases.add("美妆展示架");
            aliases.add("护肤品陈列架");
        }
        if (containsAny(lower, "toy", "game")) {
            aliases.add("玩具展示架");
        }
        return String.join(", ", aliases);
    }

    private String buildEnglishKeywords(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add("display stand");
        keywords.add("cardboard display");
        keywords.add("corrugated display");
        keywords.add("POP display");
        keywords.add("retail display");
        keywords.add("promotional display");
        if (containsAny(lower, "floor", "standing", "fsdu")) keywords.add("floor display stand");
        if (containsAny(lower, "counter", "countertop", "counter top", "cdu")) keywords.add("counter display unit");
        if (containsAny(lower, "pallet", "pdq")) keywords.add("pallet display, PDQ display");
        if (containsAny(lower, "dump bin", "bin")) keywords.add("dump bin display");
        return String.join(", ", keywords);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void persistIndex(Map<String, String> manifest) {
        if (!aiConfig.getRag().isPersistEmbeddings()) return;
        try {
            Path indexPath = embeddingIndexPath();
            Path manifestPath = embeddingManifestPath();
            Files.createDirectories(indexPath.getParent());
            Files.createDirectories(manifestPath.getParent());
            if (embeddingStore instanceof dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<TextSegment> currentStore) {
                currentStore.serializeToFile(indexPath);
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
                log.info("知识库向量索引已保存: {}", indexPath);
            } else {
                log.info("当前向量库不支持本地文件保存，跳过索引缓存");
            }
        } catch (Exception e) {
            log.warn("保存本地向量索引失败: {}", e.getMessage());
        }
    }

    private Map<String, String> loadManifest() {
        if (!aiConfig.getRag().isPersistEmbeddings()) return Map.of();
        if (!Files.exists(embeddingIndexPath())) return Map.of();
        Path manifestPath = embeddingManifestPath();
        if (!Files.exists(manifestPath)) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(manifestPath.toFile(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("读取向量 manifest 失败，将按空索引处理: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean isPersistedIndexPresent() {
        return !aiConfig.getRag().isPersistEmbeddings() || Files.exists(embeddingIndexPath());
    }

    private List<IndexItem> buildIndexItems(List<TextSegment> knowledgeSegments, List<TextSegment> productSegments) {
        List<IndexItem> items = new ArrayList<>();
        for (TextSegment segment : knowledgeSegments) {
            items.add(toIndexItem(segment));
        }
        if (aiConfig.getRag().isIndexProducts()) {
            int limit = Math.max(0, aiConfig.getRag().getMaxProductEmbeddings());
            List<TextSegment> indexedProducts = limit == 0 ? productSegments : productSegments.stream().limit(limit).toList();
            for (TextSegment segment : indexedProducts) {
                items.add(toIndexItem(segment));
            }
        }
        return items;
    }

    private IndexItem toIndexItem(TextSegment segment) {
        String source = segment.metadata() != null ? segment.metadata().getString("source") : "unknown";
        String key = segment.metadata() != null ? segment.metadata().getString("indexKey") : null;
        if (key == null || key.isBlank()) {
            key = source + ":" + sha256(segment.text()).substring(0, 16);
            Metadata metadata = segment.metadata() != null ? segment.metadata().copy() : new Metadata();
            metadata.put("indexKey", key);
            metadata.put("source", source);
            segment = TextSegment.from(segment.text(), metadata);
        }
        return new IndexItem(key, source, sha256(source + "\n" + segment.text()), segment);
    }

    private void removeChangedItems(Set<String> changedKeys) {
        if (changedKeys.isEmpty()) return;
        try {
            embeddingStore.removeAll(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                    .metadataKey("indexKey")
                    .isIn(changedKeys));
        } catch (Exception e) {
            log.warn("删除已变化向量失败，将继续追加新向量: {}", e.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private Path embeddingIndexPath() {
        String configured = aiConfig.getRag().getEmbeddingIndexPath();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".jc-agent", "rag", "embedding-index.json");
    }

    private Path embeddingFingerprintPath() {
        String configured = aiConfig.getRag().getEmbeddingFingerprintPath();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".jc-agent", "rag", "embedding-index.sha256");
    }

    private Path embeddingManifestPath() {
        String configured = aiConfig.getRag().getEmbeddingManifestPath();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".jc-agent", "rag", "embedding-manifest.json");
    }

    private record IndexItem(String key, String source, String hash, TextSegment segment) {}
}
