package com.sky.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeIndexConfig;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Value("${pinecone.api-key}")
    private String pineconeApiKey;

    @Value("${pinecone.index}")
    private String pineconeIndex;

    @Value("${pinecone.namespace}")
    private String pineconeNamespace;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        // 使用 PineconeEmbeddingStore 创建一个 EmbeddingStore 实例
        // 注意：从配置文件读取实际的 API Key，而不是硬编码字符串
        EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
                .apiKey(pineconeApiKey)  // 从配置文件读取真实的 API Key
                .index(pineconeIndex)    // 从配置文件读取索引名称
                .nameSpace(pineconeNamespace)  // 从配置文件读取命名空间
                .createIndex(PineconeServerlessIndexConfig.builder()
                        .cloud("aws")    // 云服务商
                        .region("us-east-1")  // 区域
                        .dimension(embeddingModel.dimension())
                        .build())
                .build();
        return embeddingStore;
    }
}
