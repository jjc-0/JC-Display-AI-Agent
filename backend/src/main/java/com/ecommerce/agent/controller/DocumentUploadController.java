package com.ecommerce.agent.controller;

import com.ecommerce.agent.model.KnowledgeDocument;
import com.ecommerce.agent.rag.KnowledgeBaseLoader;
import com.ecommerce.agent.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 知识文档管理 API
 * - 上传 PDF/Word/TXT 文档
 * - 查看/编辑/删除文档
 * - 重建索引
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class DocumentUploadController {

    private final KnowledgeDocumentService docService;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    /**
     * 上传文档（PDF/Word/TXT）
     */
    @PostMapping("/knowledge/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
            }

            long maxSize = 10 * 1024 * 1024; // 10MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "文件过大，上限 10MB",
                        "fileSize", file.getSize()
                ));
            }

            KnowledgeDocument doc = docService.uploadDocument(file);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文档已上传并解析成功",
                    "document", toDocMap(doc)
            ));
        } catch (Exception e) {
            log.error("文档上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/knowledge/upload/batch")
    public ResponseEntity<Map<String, Object>> uploadDocuments(
            @RequestParam("files") List<MultipartFile> files) {
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (MultipartFile file : files) {
            try {
                KnowledgeDocument doc = docService.uploadDocument(file);
                results.add(Map.of(
                        "fileName", file.getOriginalFilename(),
                        "status", "success",
                        "id", doc.getId()
                ));
                successCount++;
            } catch (Exception e) {
                results.add(Map.of(
                        "fileName", file.getOriginalFilename(),
                        "status", "failed",
                        "error", e.getMessage()
                ));
                failCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", failCount == 0,
                "successCount", successCount,
                "failCount", failCount,
                "results", results
        ));
    }

    /**
     * 文档列表（支持分页和筛选）
     */
    @GetMapping("/knowledge/documents/paged")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String keyword) {

        Page<KnowledgeDocument> docs = docService.listDocuments(page, size, sourceType, keyword);

        List<Map<String, Object>> items = docs.getContent().stream()
                .map(DocumentUploadController::toDocMap)
                .toList();

        return ResponseEntity.ok(Map.of(
                "total", docs.getTotalElements(),
                "page", page,
                "size", size,
                "totalPages", docs.getTotalPages(),
                "items", items
        ));
    }

    /**
     * 更新文档内容
     */
    @PutMapping("/knowledge/documents/{id}")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            KnowledgeDocument doc = docService.updateDocument(
                    id,
                    body.get("title"),
                    body.get("content"),
                    body.get("category")
            );
            return ResponseEntity.ok(Map.of("success", true, "document", toDocMap(doc)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 切换文档启用/禁用
     */
    @PutMapping("/knowledge/documents/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleDocument(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        docService.toggleEnabled(id, enabled);
        return ResponseEntity.ok(Map.of("success", true, "id", id, "enabled", enabled));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/knowledge/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            docService.deleteDocument(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "文档已删除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 重建 RAG 索引
     */
    @PostMapping("/knowledge/rebuild-index")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        Map<String, Object> progress = knowledgeBaseLoader.startAsyncReload("manual");
        Map<String, Object> result = new LinkedHashMap<>(progress);
        result.put("success", true);
        result.putIfAbsent("message", "索引更新已启动");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/knowledge/rebuild-index/progress")
    public ResponseEntity<Map<String, Object>> rebuildIndexProgress() {
        return ResponseEntity.ok(knowledgeBaseLoader.getIndexProgress());
    }

    /**
     * 统计信息
     */
    @GetMapping("/knowledge/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // 由 AgentController 中的 /knowledge/status 覆盖，这里做个补充
        return ResponseEntity.ok(Map.of("available", true));
    }

    private static Map<String, Object> toDocMap(KnowledgeDocument doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("category", doc.getCategory() != null ? doc.getCategory() : "");
        m.put("sourceType", doc.getSourceType() != null ? doc.getSourceType() : "BUILT_IN");
        m.put("fileType", doc.getFileType() != null ? doc.getFileType() : "MARKDOWN");
        m.put("fileName", doc.getFileName() != null ? doc.getFileName() : "");
        m.put("enabled", doc.isEnabled());
        m.put("contentLength", doc.getContent() != null ? doc.getContent().length() : 0);
        m.put("contentPreview", doc.getContent() != null
                ? doc.getContent().substring(0, Math.min(200, doc.getContent().length())) : "");
        m.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
        m.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : "");
        return m;
    }
}
