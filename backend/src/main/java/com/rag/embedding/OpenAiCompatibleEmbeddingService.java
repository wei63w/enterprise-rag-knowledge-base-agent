package com.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    @Autowired
    public OpenAiCompatibleEmbeddingService(EmbeddingProperties properties) {
        this(createEmbeddingModel(properties), properties);
    }

    OpenAiCompatibleEmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<TextSegment> segments = texts.subList(start, end).stream()
                    .map(TextSegment::from)
                    .collect(Collectors.toList());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddings.stream()
                    .map(Embedding::vector)
                    .forEach(result::add);
        }
        return result;
    }

    @Override
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }

    private static EmbeddingModel createEmbeddingModel(EmbeddingProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
    }
}
