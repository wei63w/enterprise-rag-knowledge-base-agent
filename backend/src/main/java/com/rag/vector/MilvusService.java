package com.rag.vector;

import com.rag.embedding.EmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MilvusService {

    private final MilvusProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private EmbeddingStore<TextSegment> embeddingStore;

    public MilvusService(MilvusProperties properties, EmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    @PostConstruct
    public void init() {
        embeddingStore = MilvusEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .collectionName(properties.getCollection())
                .dimension(properties.getDimension())
                .build();
    }

    public void insert(List<String> chunkIds, List<String> contents, List<float[]> embeddings) {
        List<Embedding> embeddingList = embeddings.stream()
                .map(Embedding::from)
                .collect(Collectors.toList());
        List<TextSegment> segments = contents.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        embeddingStore.addAll(embeddingList, segments);
    }

    public List<EmbeddingMatch<TextSegment>> search(float[] queryVector, int topK) {
        Embedding queryEmbedding = Embedding.from(queryVector);
        return embeddingStore.findRelevant(queryEmbedding, topK);
    }
}