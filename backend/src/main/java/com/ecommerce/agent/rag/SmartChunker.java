package com.ecommerce.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 智能分块器 — 按文档结构（标题/段落）分块，保持语义完整性
 *
 * 策略：
 * - Markdown/富文本：按 ## 标题拆分 → 每个段落不超过 chunkSize
 * - 纯文本：按双换行（段落边界）拆分 → 长段落按句子进一步拆分
 * - 产品数据：每个产品就是一个独立 chunk（由调用方处理）
 * - 重叠策略：相邻 chunk 共享 overlapSize 字符，避免关键信息被切分
 */
@Component
public class SmartChunker {

    private static final int DEFAULT_CHUNK_SIZE = 800;   // 每 chunk 最大字符数
    private static final int DEFAULT_OVERLAP = 100;       // 相邻 chunk 重叠字符数
    private static final int MIN_CHUNK_SIZE = 100;        // 最小 chunk（过短不切）

    // Markdown 标题检测
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{2,4}\\s", Pattern.MULTILINE);
    // 双换行（段落边界）
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");

    /**
     * 对文档列表进行智能分块
     * @param documents  待索引的原始文档
     * @param source     来源标签（如 "products", "knowledge", "user_upload"）
     * @return 已分块 + 打标 source 的 TextSegment 列表
     */
    public List<TextSegment> chunk(List<Document> documents, String source) {
        return chunk(documents, source, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<TextSegment> chunk(List<Document> documents, String source, int chunkSize, int overlap) {
        List<TextSegment> segments = new ArrayList<>();
        for (Document doc : documents) {
            segments.addAll(chunkDocument(doc, source, chunkSize, overlap));
        }
        return segments;
    }

    /**
     * 对单个文档进行智能分块
     */
    private List<TextSegment> chunkDocument(Document doc, String source, int chunkSize, int overlap) {
        String text = doc.text();
        if (text == null || text.isBlank()) return List.of();

        List<TextSegment> segments = new ArrayList<>();

        // 策略1: 有标题 → 按 ## 标题拆分
        List<String> sections = splitByHeadings(text);
        if (sections.size() > 1) {
            for (String section : sections) {
                segments.addAll(splitByLengthWithOverlap(section, source, chunkSize, overlap));
            }
        } else {
            // 策略2: 无标题 → 按段落边界拆分
            segments.addAll(splitByParagraphs(text, source, chunkSize, overlap));
        }

        return segments;
    }

    /**
     * 按 ## 标题拆分为段落
     */
    List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        String[] parts = HEADING_PATTERN.split(text);

        // 找到所有标题位置
        List<String> headings = new ArrayList<>();
        var matcher = HEADING_PATTERN.matcher(text);
        while (matcher.find()) {
            headings.add(matcher.group());
        }

        // 重新组装配对：第一个部分没有标题前缀
        if (parts.length > 0 && !parts[0].isBlank()) {
            sections.add(parts[0].trim());
        }

        for (int i = 0; i < headings.size() && i + 1 < parts.length; i++) {
            String section = headings.get(i) + parts[i + 1];
            if (!section.isBlank()) {
                sections.add(section.trim());
            }
        }

        return sections;
    }

    /**
     * 按段落边界拆分后按长度进一步切割
     */
    private List<TextSegment> splitByParagraphs(String text, String source, int chunkSize, int overlap) {
        List<TextSegment> segments = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_BOUNDARY.split(text);

        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isBlank()) continue;

            if (current.length() + trimmed.length() + 2 > chunkSize && current.length() >= MIN_CHUNK_SIZE) {
                segments.add(TextSegment.from(current.toString().trim(),
                        new dev.langchain4j.data.document.Metadata().put("source", source)));
                // 保留重叠部分
                int overlapStart = Math.max(0, current.length() - overlap);
                String overlapText = current.substring(overlapStart);
                current = new StringBuilder(overlapText).append("\n\n").append(trimmed);
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(trimmed);
            }
        }

        if (current.length() >= MIN_CHUNK_SIZE) {
            segments.add(TextSegment.from(current.toString().trim(),
                    new dev.langchain4j.data.document.Metadata().put("source", source)));
        }

        return segments;
    }

    /**
     * 按长度硬切（带重叠）— 用于标题拆分后的子段落
     */
    private List<TextSegment> splitByLengthWithOverlap(String text, String source, int chunkSize, int overlap) {
        List<TextSegment> segments = new ArrayList<>();
        if (text.length() <= chunkSize) {
            segments.add(TextSegment.from(text,
                    new dev.langchain4j.data.document.Metadata().put("source", source)));
            return segments;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // 尽量在句子边界断开
            if (end < text.length()) {
                int breakPoint = findBestBreakPoint(text, end, end - start);
                if (breakPoint > start + MIN_CHUNK_SIZE) {
                    end = breakPoint;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (chunk.length() >= MIN_CHUNK_SIZE) {
                segments.add(TextSegment.from(chunk,
                        new dev.langchain4j.data.document.Metadata().put("source", source)));
            }
            start = end - overlap;
            if (start >= text.length()) break;
        }
        return segments;
    }

    /**
     * 在句子/段落边界找到最佳断开点
     */
    private int findBestBreakPoint(String text, int target, int range) {
        int searchStart = Math.max(0, target - range);
        String window = text.substring(searchStart, Math.min(target + 50, text.length()));
        // 优先级: 双换行 > 单换行 > 句号 > 分号 > 逗号
        for (String delim : new String[]{"\n\n", "\n", ". ", "。", "; ", "；", ", ", "，"}) {
            int idx = window.lastIndexOf(delim);
            if (idx >= 0 && (searchStart + idx) > searchStart + MIN_CHUNK_SIZE) {
                return searchStart + idx + delim.length();
            }
        }
        return target;
    }
}
