package com.ecommerce.agent.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置
 * - EmbeddingModel: DeepSeek API（OpenAI 兼容格式，尝试 /v1/embeddings）
 * - EmbeddingStore: 内存存储（启动时从 MySQL 重建）
 */
@Configuration
public class RAGConfig {

    @Value("${DEEPSEEK_API_KEY:sk-placeholder}")
    private String apiKey;

    @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("text-embedding-v2")
                .maxRetries(1)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
