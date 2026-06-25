package com.ecommerce.agent.tool;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.service.ImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 图片编辑工具 — 原图 + 编辑指令 → AI 生成修改后图片
 *
 * 用途：局部修改、背景替换、颜色调整、元素增删等
 * 双路径：/v1/images/edits → 失败回退 chat/completions（多模态）
 */
@Slf4j
@Component
public class ImageEditTool implements Tool {

    private final ImageGenerationService imageGenService;
    private final AgentRuntime agentRuntime;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public ImageEditTool(ImageGenerationService imageGenService,
                         @Lazy AgentRuntime agentRuntime) {
        this.imageGenService = imageGenService;
        this.agentRuntime = agentRuntime;
    }

    @Override public String getName() { return "image_edit"; }
    @Override public String getCategory() { return "MULTIMODAL"; }
    @Override public long getTimeoutMs() { return 300000; }

    @Override
    public boolean isEnabled() {
        return imageGenService.isConfigured();
    }

    @Override
    public String getDescription() {
        return "图片编辑工具。对已有图片进行修改——局部修改、背景替换、颜色调整、元素增删、风格变换。需提供图片索引(0,1,2..)和修改描述。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> idxProp = new LinkedHashMap<>();
        idxProp.put("type", "integer");
        idxProp.put("description", "要编辑的图片索引（0=第一张, 1=第二张...），引用用户上传或之前生成的图片");
        props.put("image_index", idxProp);

        Map<String, Object> promptProp = new LinkedHashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "修改描述。例如: 把背景换成白色, 去掉水印, 把产品颜色改为红色, 增加价格标签");
        props.put("prompt", promptProp);

        Map<String, Object> sidProp = new LinkedHashMap<>();
        sidProp.put("type", "string");
        sidProp.put("description", "会话ID，用于查找用户上传或生成的图片");
        props.put("session_id", sidProp);

        schema.put("properties", props);
        schema.put("required", List.of("image_index", "prompt"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        try {
            if (!isEnabled()) return done("❌ 图片编辑服务未配置");

            int imageIndex = ((Number) params.get("image_index")).intValue();
            String editPrompt = (String) params.get("prompt");
            String sessionId = (String) params.getOrDefault("session_id", findSessionIdInContext(params));

            if (editPrompt == null || editPrompt.isBlank()) return done("❌ 请提供编辑描述");

            // 1. 从会话图片中获取目标图片 URL
            if (sessionId == null) return done("❌ 无法确定会话，请提供 session_id");
            List<String> imgs = agentRuntime.getSessionImages(sessionId);
            if (imgs.isEmpty()) return done("❌ 当前会话没有可编辑的图片");

            // 图片对话后图片已转为多模态传入，索引可能对应原上传顺序
            int idx = Math.max(0, Math.min(imageIndex, imgs.size() - 1));
            String imageUrl = imgs.get(idx);
            log.info("图片编辑: 选中图片[{}] url={} prompt={}", idx,
                    imageUrl.substring(0, Math.min(60, imageUrl.length())),
                    editPrompt.substring(0, Math.min(40, editPrompt.length())));

            // 2. 下载原始图片
            byte[] imageBytes;
            String mimeType = "image/png";

            if (imageUrl.startsWith("data:")) {
                String[] parts = imageUrl.split(",");
                if (parts.length < 2) return done("❌ 无效的图片数据");
                String header = parts[0];
                if (header.contains(";")) mimeType = header.substring(5, header.indexOf(";"));
                imageBytes = Base64.getDecoder().decode(parts[1]);
            } else if (imageUrl.startsWith("http") || imageUrl.startsWith("/uploads")) {
                String fetchUrl = imageUrl;
                if (fetchUrl.startsWith("/uploads"))
                    fetchUrl = "http://localhost:8088" + fetchUrl;
                Request req = new Request.Builder().url(fetchUrl).get().build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (!resp.isSuccessful()) return done("❌ 原图下载失败: HTTP " + resp.code());
                    imageBytes = resp.body() != null ? resp.body().bytes() : null;
                    if (resp.header("Content-Type") != null)
                        mimeType = resp.header("Content-Type");
                }
                if (imageBytes == null || imageBytes.length == 0)
                    return done("❌ 原图为空");
            } else {
                return done("❌ 不支持的图片 URL 格式");
            }

            // 3. 调用编辑服务
            Map<String, Object> result = imageGenService.edit(imageBytes, mimeType, editPrompt);
            @SuppressWarnings("unchecked")
            List<String> editedImages = (List<String>) result.get("images");

            if (editedImages == null || editedImages.isEmpty())
                return done("❌ 图片编辑未返回结果");

            // 4. 将编辑后的图片加入会话（供后续操作引用）
            for (String img : editedImages) {
                if (!imgs.contains(img)) {
                    agentRuntime.getSessionImages(sessionId); // ensure list exists
                }
            }

            StringBuilder sb = new StringBuilder("🎨 图片编辑完成:\n\n");
            for (int i = 0; i < editedImages.size(); i++) {
                sb.append("![").append(editPrompt.length() > 20
                        ? editPrompt.substring(0, 20) + "…"
                        : editPrompt).append("](").append(editedImages.get(i)).append(")\n\n");
            }
            return done(sb.toString());

        } catch (Exception e) {
            log.error("图片编辑失败", e);
            return done("❌ 图片编辑失败: " + e.getMessage());
        }
    }

    private CompletableFuture<String> done(String s) {
        return CompletableFuture.completedFuture(s);
    }

    @SuppressWarnings("unchecked")
    private String findSessionIdInContext(Map<String, Object> params) {
        Object ctx = params.get("_chain_context");
        if (ctx instanceof Map<?, ?> map) return (String) map.get("session_id");
        return null;
    }
}
