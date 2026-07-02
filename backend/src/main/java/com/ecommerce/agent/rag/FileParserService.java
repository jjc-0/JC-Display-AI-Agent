package com.ecommerce.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 文档解析服务 — 支持 PDF、Word (.docx)、纯文本
 * 将上传的文件解析为纯文本，供 RAG 知识库向量索引
 */
@Slf4j
@Service
public class FileParserService {

    private final Tika tika = new Tika();

    /**
     * 解析上传文件 → 纯文本
     * @return 提取的文本内容
     */
    public ParsedDocument parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String mimeType = tika.detect(file.getInputStream());
        byte[] bytes = file.getBytes();

        log.info("解析文件: {} (type={}, size={}KB)", fileName, mimeType, bytes.length / 1024);

        String text;

        if (mimeType != null && mimeType.equals("application/pdf")) {
            text = parsePdf(bytes, fileName);
        } else if (isSpreadsheet(mimeType, fileName)) {
            text = parseSpreadsheet(bytes, fileName);
        } else if (mimeType != null && (mimeType.contains("officedocument") ||
                fileName.toLowerCase().endsWith(".docx"))) {
            text = parseDocx(bytes, fileName);
        } else {
            // 默认当作文本内容处理
            text = parseText(bytes, fileName);
        }

        if (text == null || text.isBlank()) {
            throw new IOException("文件解析后内容为空: " + fileName);
        }

        return new ParsedDocument(fileName, mimeType, text.trim(), text.length());
    }

    private String parsePdf(byte[] bytes, String fileName) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            String text = stripper.getText(document);
            log.info("PDF解析完成: {}, {} 字符", fileName, text.length());
            return text;
        }
    }

    private String parseDocx(byte[] bytes, String fileName) throws IOException {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            // 也提取表格内容
            doc.getTables().forEach(table ->
                table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> {
                        String t = cell.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t).append(" | ");
                        }
                    })
                )
            );
            log.info("Word解析完成: {}, {} 字符", fileName, sb.length());
            return sb.toString();
        }
    }

    private String parseSpreadsheet(byte[] bytes, String fileName) throws IOException {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();

            workbook.forEach(sheet -> {
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                int lastRow = sheet.getLastRowNum();
                for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
                    var row = sheet.getRow(rowIndex);
                    if (row == null) continue;
                    short lastCell = row.getLastCellNum();
                    if (lastCell <= 0) continue;

                    StringBuilder line = new StringBuilder();
                    for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                        var cell = row.getCell(cellIndex);
                        String value = cell != null ? formatter.formatCellValue(cell).trim() : "";
                        if (cellIndex > 0) line.append(" | ");
                        line.append(value);
                    }
                    String rowText = line.toString().replaceAll("(\\s*\\|\\s*)+$", "").trim();
                    if (!rowText.isBlank()) {
                        sb.append(rowText).append("\n");
                    }
                }
                sb.append("\n");
            });

            log.info("Excel解析完成: {}, {} 字符", fileName, sb.length());
            return sb.toString();
        }
    }

    private String parseText(byte[] bytes, String fileName) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        log.info("文本解析完成: {}, {} 字符", fileName, text.length());
        return text;
    }

    private boolean isSpreadsheet(String mimeType, String fileName) {
        String mime = mimeType != null ? mimeType.toLowerCase() : "";
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        return mime.contains("spreadsheet")
                || mime.contains("excel")
                || lowerName.endsWith(".xlsx")
                || lowerName.endsWith(".xls");
    }

    /**
     * 解析结果
     */
    public record ParsedDocument(
            String fileName,
            String mimeType,
            String content,
            int charCount
    ) {}
}
