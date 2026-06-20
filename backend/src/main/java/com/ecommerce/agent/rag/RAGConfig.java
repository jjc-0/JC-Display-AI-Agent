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
 * - EmbeddingModel: DashScope text-embedding-v1（兼容 OpenAI API，1024维）
 * - EmbeddingStore: 内存存储（启动时从 MySQL 重建）
 */
@Configuration
public class RAGConfig {

    @Value("${OPENAI_API_KEY:sk-placeholder}")
    private String apiKey;

    @Value("${OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("text-embedding-v1")
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
