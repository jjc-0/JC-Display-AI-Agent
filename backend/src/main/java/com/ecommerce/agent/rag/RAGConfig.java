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
 * - EmbeddingModel: 使用 OpenAI 兼容 API（DeepSeek 不支持 /v1/embeddings）
 * - EmbeddingStore: 内存存储，首次查询时懒加载
 */
@Configuration
public class RAGConfig {

    @Value("${OPENAI_API_KEY:sk-placeholder}")
    private String apiKey;

    @Value("${OPENAI_BASE_URL:https://api.openai.com/v1}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("text-embedding-3-small")
                .maxRetries(1)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
