package com.ecommerce.agent.service;

import com.ecommerce.agent.model.KnowledgeDocument;
import com.ecommerce.agent.rag.FileParserService;
import com.ecommerce.agent.rag.KnowledgeBaseLoader;
import com.ecommerce.agent.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 知识文档管理服务
 * - 查询/分页
 * - 用户上传（PDF/Word/TXT）
 * - 更新/启用禁用
 * - 删除 + 清理关联向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository repo;
    private final FileParserService fileParser;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    /**
     * 分页查询文档列表
     */
    public Page<KnowledgeDocument> listDocuments(int page, int size, String sourceType, String keyword) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        if (keyword != null && !keyword.isBlank()) {
            return repo.findByKeyword(keyword, pr);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            return repo.findBySourceType(sourceType, pr);
        }
        return repo.findAll(pr);
    }

    /**
     * 上传文档 → 解析 → 持久化 → 重新索引
     */
    @Transactional
    public KnowledgeDocument uploadDocument(MultipartFile file) throws IOException {
        // 1. 解析文件
        FileParserService.ParsedDocument parsed = fileParser.parse(file);

        // 2. 确定文件类型
        String fileType = resolveFileType(parsed.fileName(), parsed.mimeType());

        // 3. 构造标题（取文件名去掉扩展名）
        String title = parsed.fileName().replaceAll("\\.[^.]+$", "");
        if (title.length() > 100) title = title.substring(0, 100);

        // 4. 持久化到 MySQL
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .title(title)
                .content(parsed.content())
                .category("user_upload")
                .sourceType("USER_UPLOAD")
                .fileType(fileType)
                .fileName(parsed.fileName())
                .enabled(true)
                .build();
        doc = repo.save(doc);

        log.info("文档已保存: id={}, title={}, type={}, chars={}",
                doc.getId(), doc.getTitle(), fileType, parsed.charCount());

        // 5. 触发 RAG 重新索引（延迟策略，避免频繁重建）
        // 这里改为增量索引是更好的选择，但当前架构暂用全量重建
        // 用一个 debounce 计数器 — 每上传 3 个文档才触发重建
        long totalUserDocs = repo.countBySourceType("USER_UPLOAD");
        if (totalUserDocs % 3 == 0) {
            knowledgeBaseLoader.forceReload();
            log.info("已触发知识库重新索引 (累计{}个用户文档)", totalUserDocs);
        }

        return doc;
    }

    /**
     * 更新文档内容
     */
    @Transactional
    public KnowledgeDocument updateDocument(Long id, String title, String content, String category) {
        KnowledgeDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        if (title != null && !title.isBlank()) doc.setTitle(title);
        if (content != null && !content.isBlank()) doc.setContent(content);
        if (category != null) doc.setCategory(category);
        doc.setUpdatedAt(LocalDateTime.now());
        return repo.save(doc);
    }

    /**
     * 切换启用/禁用
     */
    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        KnowledgeDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        doc.setEnabled(enabled);
        repo.save(doc);
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long id) {
        repo.deleteById(id);
    }

    /**
     * 重建知识库向量索引
     */
    public void rebuildIndex() {
        knowledgeBaseLoader.forceReload();
    }

    private String resolveFileType(String fileName, String mimeType) {
        if (mimeType == null) mimeType = "";
        if (mimeType.equals("application/pdf")) return "PDF";
        if (mimeType.contains("officedocument") || mimeType.contains("word")) return "DOCX";
        if (fileName.toLowerCase().endsWith(".pdf")) return "PDF";
        if (fileName.toLowerCase().endsWith(".docx")) return "DOCX";
        if (fileName.toLowerCase().endsWith(".doc")) return "DOC";
        return "TXT";
    }
}
