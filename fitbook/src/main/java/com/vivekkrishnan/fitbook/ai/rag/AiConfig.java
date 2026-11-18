package com.vivekkrishnan.fitbook.ai.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Milestone 4: infrastructure beans for the AI features. The vector store is Spring AI's
// in-memory SimpleVectorStore: no extra container to run, and the knowledge base is small
// enough to re-embed on demand.
@Configuration
public class AiConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
