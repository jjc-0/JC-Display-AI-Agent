package com.ecommerce.agent.service.v2;

import com.ecommerce.agent.model.ConversationMessage;
import com.ecommerce.agent.model.v2.Customer;
import com.ecommerce.agent.model.v2.MemoryEntry;
import com.ecommerce.agent.repository.CustomerRepository;
import com.ecommerce.agent.repository.MemoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * v2 长期记忆系统
 *
 * 三层记忆架构:
 * - Working Memory: ConversationManager (现有, 最近10轮对话)
 * - Long-term Memory: 本服务 (MySQL + 向量检索)
 * - Customer Memory: 客户档案 (Customer实体)
 *
 * 向量检索策略 (过渡期方案):
 * - 查询时: embedding(question) → 加载用户记忆 → 余弦相似度排序 → Top-K
 * - 未来: pgvector → 直接向量检索
 */
@Slf4j
@Service
public class MemoryService {

    private final MemoryRepository memoryRepo;
    private final CustomerRepository customerRepo;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public MemoryService(MemoryRepository memoryRepo,
                         CustomerRepository customerRepo,
                         EmbeddingModel embeddingModel) {
        this.memoryRepo = memoryRepo;
        this.customerRepo = customerRepo;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
    }

    // ═══════════════════════════════════════════════════════════════
    // Long-term Memory CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * 保存长期记忆 (自动生成 embedding)
     */
    @Transactional
    public MemoryEntry store(String userId, String type, String summary, String content,
                             String customerId, List<String> tags, int importance) {
        MemoryEntry entry = MemoryEntry.builder()
                .userId(userId)
                .type(type)
                .summary(summary)
                .content(content)
                .customerId(customerId)
                .tags(tags != null ? String.join(",", tags) : null)
                .importance(importance)
                .build();

        // 生成 embedding (异步场景可改为队列, 当前同步)
        try {
            Embedding emb = embeddingModel.embed(summary + " " + content).content();
            entry.setEmbedding(encodeEmbedding(emb));
        } catch (Exception e) {
            log.warn("生成 embedding 失败: {}", e.getMessage());
        }

        return memoryRepo.save(entry);
    }

    /**
     * 从对话中提取并存储记忆 (自动触发)
     */
    @Transactional
    public void learnFromConversation(String userId, String sessionId,
                                       List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        // 提取用户说的内容作为候选记忆
        String userContent = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(ConversationMessage::getContent)
                .collect(Collectors.joining(" | "));
        if (userContent.isBlank()) return;

        // 取前200字符做摘要
        String summary = userContent.length() > 200 ? userContent.substring(0, 200) + "..." : userContent;

        store(userId, "KNOWLEDGE", summary, userContent,
                null, List.of("conversation"), 3);
        log.debug("从对话学习: userId={}, sessionId={}, chars={}", userId, sessionId, userContent.length());
    }

    /**
     * 存储客户记忆 (客户信息快照)
     */
    @Transactional
    public MemoryEntry storeCustomerMemory(Long customerId, Map<String, Object> customerData) {
        String cid = String.valueOf(customerId);
        try {
            String json = objectMapper.writeValueAsString(customerData);
            return store(null, "CUSTOMER",
                    "客户信息快照 #" + customerId,
                    json, cid, List.of("customer", "snapshot"), 7);
        } catch (JsonProcessingException e) {
            log.error("序列化客户数据失败", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Vector Search
    // ═══════════════════════════════════════════════════════════════

    /**
     * 向量语义检索 — 在当前用户的所有记忆中搜索
     * @return 按相似度排序的 Top-K 记忆条目
     */
    public List<MemoryHit> vectorSearch(String userId, String query, int topK) {
        // 1. 生成查询向量
        Embedding queryEmb;
        try {
            queryEmb = embeddingModel.embed(query).content();
        } catch (Exception e) {
            log.warn("查询向量化失败: {}", e.getMessage());
            return List.of();
        }

        // 2. 加载用户所有未归档记忆
        List<MemoryEntry> candidates = memoryRepo.findByUserIdAndArchivedFalseOrderByImportanceDesc(userId);
        if (candidates.isEmpty()) return List.of();

        // 3. 计算余弦相似度
        float[] queryVec = queryEmb.vectorAsList().stream()
                .mapToDouble(Float::floatValue)
                .collect(() -> new float[queryEmb.vectorAsList().size()],
                        (arr, d) -> arr[arr.length == 0 ? 0 : Math.min(arr.length - 1, (int) arr[0])] = (float) d,
                        (a, b) -> {});

        List<MemoryHit> hits = new ArrayList<>();
        for (MemoryEntry mem : candidates) {
            if (mem.getEmbedding() == null) continue;
            double similarity = cosineSimilarity(queryVec, decodeEmbedding(mem.getEmbedding()));
            if (similarity > 0.5) { // 阈值
                hits.add(new MemoryHit(mem, similarity));
                mem.setAccessCount(mem.getAccessCount() + 1); // 增加访问计数
            }
        }

        hits.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        List<MemoryHit> result = hits.subList(0, Math.min(topK, hits.size()));

        // 批量更新访问计数 (简单实现)
        memoryRepo.saveAll(result.stream().map(MemoryHit::entry).toList());

        log.debug("向量检索: userId={}, query={}, results={}", userId, truncate(query, 30), result.size());
        return result;
    }

    /**
     * 获取 Top-N 最重要的记忆 (不依赖向量)
     */
    public List<MemoryEntry> getTopMemories(String userId, int limit) {
        return memoryRepo.findTop10ByUserIdAndArchivedFalseOrderByImportanceDescAccessCountDesc(userId)
                .stream().limit(limit).toList();
    }

    /**
     * 构建增强 Prompt 的上下文 (注入相关记忆)
     */
    public String buildMemoryContext(String userId, String currentQuery, int maxMemories) {
        List<MemoryHit> hits = vectorSearch(userId, currentQuery, maxMemories);
        if (hits.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("## 相关历史记忆\n");
        for (int i = 0; i < hits.size(); i++) {
            MemoryHit hit = hits.get(i);
            sb.append(i + 1).append(". [").append(hit.entry.getType()).append("] ")
                    .append(hit.entry.getSummary()).append("\n");
        }
        sb.append("\n请结合以上历史信息回答用户问题。\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // Customer Context
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建客户上下文 (给 Agent Runtime 使用)
     */
    public String buildCustomerContext(String customerId) {
        Optional<Customer> customer = customerRepo.findById(Long.valueOf(customerId));
        if (customer.isEmpty()) return null;

        Customer c = customer.get();
        return String.format("""
                ## 当前客户信息
                - 公司: %s
                - 国家: %s
                - 行业: %s
                - 状态: %s
                - 上次联系: %s
                - 产品偏好: %s
                - 关键需求: %s
                """,
                c.getName(), c.getCountry(), c.getIndustry(), c.getStatus(),
                c.getLastContactAt() != null ? c.getLastContactAt().toString() : "无",
                c.getProductPreferences() != null ? c.getProductPreferences() : "未知",
                c.getRequirements() != null ? c.getRequirements() : "未知"
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Maintenance
    // ═══════════════════════════════════════════════════════════════

    /**
     * 归档旧记忆 (importance < 3 且 30天未访问)
     */
    @Transactional
    public void archiveStaleMemories(String userId) {
        List<MemoryEntry> stale = memoryRepo.findByUserIdAndArchivedFalseOrderByImportanceDesc(userId)
                .stream()
                .filter(m -> m.getImportance() < 3
                        && m.getAccessCount() == 0
                        && m.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30)))
                .toList();
        stale.forEach(m -> m.setArchived(true));
        memoryRepo.saveAll(stale);
        log.info("归档旧记忆: userId={}, count={}", userId, stale.size());
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private String encodeEmbedding(Embedding emb) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(emb.vectorAsList().size() * 4);
        emb.vectorAsList().forEach(f -> buf.putInt(Float.floatToIntBits(f)));
        return Base64.getEncoder().encodeToString(buf.array());
    }

    private float[] decodeEmbedding(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) {
            int bits = ((bytes[i * 4] & 0xFF) << 24) | ((bytes[i * 4 + 1] & 0xFF) << 16)
                    | ((bytes[i * 4 + 2] & 0xFF) << 8) | (bytes[i * 4 + 3] & 0xFF);
            vec[i] = Float.intBitsToFloat(bits);
        }
        return vec;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8);
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * 记忆检索命中结果
     */
    public record MemoryHit(MemoryEntry entry, double similarity) {}
}
