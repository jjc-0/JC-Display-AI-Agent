package com.ecommerce.agent.controller;

import com.ecommerce.agent.service.ImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/product-image")
public class ProductImageController {

    private final ImageGenerationService imageGenerationService;

    public ProductImageController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    /**
     * 文字生成电商产品图
     */
    @PostMapping("/generate")
    public Map<String, Object> generateProductImage(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();

        if (!imageGenerationService.isConfigured()) {
            result.put("success", false);
            result.put("error", "图片生成服务未配置，请在 application-secrets.yml 中设置 IMAGE_GEN_ENABLED=true 并确保 OPENAI_API_KEY 已配置");
            return result;
        }

        String prompt = body.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            result.put("success", false);
            result.put("error", "请输入产品图片描述");
            return result;
        }

        String style = body.getOrDefault("style", "vivid");
        String size = body.getOrDefault("size", "1024x1024");

        try {
            Map<String, Object> genResult = imageGenerationService.generate(prompt, style, size);
            result.put("success", true);
            result.putAll(genResult);
        } catch (Exception e) {
            log.error("产品图生成失败", e);
            result.put("success", false);
            result.put("error", "生成失败: " + e.getMessage());
        }

        return result;
    }
}
