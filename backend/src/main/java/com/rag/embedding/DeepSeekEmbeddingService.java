package com.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public DeepSeekEmbeddingService(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            EmbeddingProperties properties) {
        this.properties = properties;
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        return embeddings.stream()
                .map(Embedding::vector)
                .collect(Collectors.toList());
    }

    @Override
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }
}