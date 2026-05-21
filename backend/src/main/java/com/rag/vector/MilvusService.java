package com.rag.vector;

import com.rag.embedding.EmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.dml.DeleteParam;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class MilvusService {

    private final MilvusProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private EmbeddingStore<TextSegment> embeddingStore;
    private MilvusServiceClient milvusClient;

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
        
        milvusClient = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .build());
    }

    public void insert(List<String> docIds, List<String> contents, List<float[]> embeddings) {
        List<Embedding> embeddingList = embeddings.stream()
                .map(Embedding::from)
                .collect(Collectors.toList());
        List<TextSegment> segments = IntStream.range(0, contents.size())
                .mapToObj(i -> TextSegment.from(contents.get(i), Metadata.from("docId", docIds.get(i))))
                .collect(Collectors.toList());
        embeddingStore.addAll(embeddingList, segments);
    }

    public List<EmbeddingMatch<TextSegment>> search(float[] queryVector, int topK) {
        Embedding queryEmbedding = Embedding.from(queryVector);
        return embeddingStore.findRelevant(queryEmbedding, topK);
    }

    public void deleteByDocId(String docId) {
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withExpr(String.format("docId == \"%s\"", docId))
                .build());
    }
}