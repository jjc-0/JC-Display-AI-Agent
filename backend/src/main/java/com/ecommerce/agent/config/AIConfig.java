package com.ecommerce.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    private Providers providers = new Providers();
    private Agent agent = new Agent();

    @Data
    public static class Providers {
        private OpenAIConfig openai = new OpenAIConfig();
        private DeepSeekConfig deepseek = new DeepSeekConfig();
        private ImageGenConfig imageGen = new ImageGenConfig();
    }

    @Data
    public static class OpenAIConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int maxTokens;
        private double temperature;
    }

    @Data
    public static class DeepSeekConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int maxTokens;
        private double temperature;
    }

    @Data
    public static class Agent {
        private int maxConversationRounds;
        private int contextWindowSize;
        private long toolCallTimeout;
    }

    @Data
    public static class ImageGenConfig {
        private boolean enabled = false;
        private String model = "gpt-image-2";
        private String size = "1024x1024";
        private int n = 1;
    }

    @Data
    public static class Rag {
        private int maxResults = 5;
        private double minScore = 0.6;
        private String knowledgeBasePath = "classpath:/knowledge/";
        private boolean augmentPrompt = true;
    }

    private Rag rag = new Rag();

    public boolean isDeepSeekKeyConfigured() {
        String key = providers.getDeepseek().getApiKey();
        if (key == null || key.isBlank()) return false;
        if (key.contains("placeholder") || key.contains("your-key")) return false;
        if (!key.startsWith("sk-")) return false;
        if (key.length() < 25) return false;
        for (char c : key.toCharArray()) {
            if (c > 127) return false;
        }
        return true;
    }

    /** 检查 OpenAI API Key 是否已配置（用于多模态/图片生成兜底） */
    public boolean isOpenAIKeyConfigured() {
        String key = providers.getOpenai().getApiKey();
        if (key == null || key.isBlank()) return false;
        if (key.contains("placeholder") || key.contains("your-key")) return false;
        if (!key.startsWith("sk-")) return false;
        if (key.length() < 25) return false;
        return true;
    }
}
