package com.ecommerce.agent.tool;

import com.ecommerce.agent.service.ImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 图片生成工具 — 集成到 ToolRouter
 * 文字描述 → AI 产品图
 */
@Slf4j
@Component
public class ImageGenerationTool implements Tool {

    private final ImageGenerationService imageGenService;

    public ImageGenerationTool(ImageGenerationService imageGenService) {
        this.imageGenService = imageGenService;
    }

    @Override
    public String getName() {
        return "image_generate";
    }

    @Override
    public String getDescription() {
        return "AI产品图生成工具。根据文字描述生成电商产品图，支持中英文Prompt。用于快速生成展示架/POP产品的营销图片。返回图片URL。";
    }

    @Override
    public String getCategory() {
        return "MULTIMODAL";
    }

    @Override
    public boolean isEnabled() {
        return imageGenService.isConfigured();
    }

    @Override
    public long getTimeoutMs() {
        return 180000; // 图片生成需要更长时间
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> promptProp = new LinkedHashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "图片描述文字。例如: \"一款白色纸展示架，超市零食陈列用，简洁现代设计，白色背景，专业产品摄影\"");
        props.put("prompt", promptProp);

        Map<String, Object> sizeProp = new LinkedHashMap<>();
        sizeProp.put("type", "string");
        sizeProp.put("description", "图片尺寸: 1024*1024(正方形), 720*1280(竖版), 1280*720(横版)");
        props.put("size", sizeProp);

        schema.put("properties", props);
        schema.put("required", List.of("prompt"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = (String) params.get("prompt");
                String size = (String) params.getOrDefault("size", "1024*1024");

                if (!imageGenService.isConfigured()) {
                    return "❌ 图片生成服务未配置，请在 application-secrets.yml 中设置 IMAGE_GEN_API_KEY";
                }

                Map<String, Object> result = imageGenService.generate(prompt, "vivid", size)
                        .get(180, java.util.concurrent.TimeUnit.SECONDS);

                @SuppressWarnings("unchecked")
                List<String> images = (List<String>) result.get("images");
                if (images == null || images.isEmpty()) {
                    return "⚠️ 图片生成请求已提交但未返回结果，可能仍在处理中。";
                }

                StringBuilder sb = new StringBuilder("🖼️ AI 产品图已生成:\n\n");
                for (int i = 0; i < images.size(); i++) {
                    sb.append("![").append(prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt)
                            .append("](").append(images.get(i)).append(")\n\n");
                }
                return sb.toString();

            } catch (Exception e) {
                log.error("图片生成失败", e);
                return "❌ 图片生成失败: " + e.getMessage();
            }
        });
    }
}
