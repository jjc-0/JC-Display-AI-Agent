package com.ecommerce.agent.controller;

import com.ecommerce.agent.llm.MultiModelOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/image")
public class ImageRecognitionController {

    private final MultiModelOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public ImageRecognitionController(MultiModelOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * 图片识别接口 — DeepSeek Vision 优先，失败自动回退 ChatGPT
     */
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> recognizeImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "请详细描述这张图片的内容，包括产品特征、颜色、材质、用途等") String prompt
    ) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("success", false);
            result.put("error", "请选择要识别的图片");
            return result;
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            result.put("success", false);
            result.put("error", "仅支持图片文件 (JPG, PNG, GIF, WebP)");
            return result;
        }

        // 校验文件大小 (最大10MB)
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            result.put("success", false);
            result.put("error", "图片大小不能超过 10MB");
            return result;
        }

        try {
            byte[] imageBytes = file.getBytes();
            log.info("收到识图请求: fileName={}, size={}KB, mime={}, prompt={}",
                    file.getOriginalFilename(),
                    file.getSize() / 1024,
                    contentType,
                    prompt.substring(0, Math.min(prompt.length(), 50)));

            // DeepSeek Vision 优先，失败自动回退 ChatGPT
            String analysis = orchestrator.recognizeImage(imageBytes, contentType, prompt).get();

            result.put("success", true);
            result.put("result", analysis);
            result.put("fileName", file.getOriginalFilename());
            result.put("size", file.getSize());

            log.info("识图完成: fileName={}, resultLength={}", file.getOriginalFilename(), analysis.length());

        } catch (Exception e) {
            log.error("图片识别失败", e);
            result.put("success", false);
            result.put("error", "识别失败: " + e.getMessage());
        }

        return result;
    }
}
