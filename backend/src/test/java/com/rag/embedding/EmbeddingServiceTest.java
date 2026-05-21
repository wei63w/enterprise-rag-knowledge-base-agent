package com.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    @Test
    void defaultsToDashScopeOpenAiCompatibleEmbedding() {
        EmbeddingProperties properties = new EmbeddingProperties();

        assertEquals("text-embedding-v4", properties.getModel());
        assertEquals(1024, properties.getDimension());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", properties.getBaseUrl());
        assertEquals(10, properties.getBatchSize());
    }

    @Test
    void embedSingleTextReturnsFloatArray() {
        EmbeddingService service = new MockEmbeddingService(1024);
        float[] result = service.embed("test text");
        assertNotNull(result);
        assertEquals(1024, result.length);
    }

    @Test
    void embedMultipleTextsReturnsListOfFloatArrays() {
        EmbeddingService service = new MockEmbeddingService(1024);
        List<float[]> result = service.embed(List.of("text1", "text2"));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1024, result.get(0).length);
    }

    @Test
    void openAiCompatibleEmbeddingServiceBatchesRequests() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(1024);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBatchSize(2);
        EmbeddingService service = new OpenAiCompatibleEmbeddingService(model, properties);

        List<float[]> result = service.embed(List.of("text1", "text2", "text3", "text4", "text5"));

        assertEquals(5, result.size());
        assertEquals(List.of(2, 2, 1), model.batchSizes);
    }

    private static class MockEmbeddingService implements EmbeddingService {
        private final int dimension;

        MockEmbeddingService(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[dimension]).toList();
        }

        @Override
        public float[] embed(String text) {
            return new float[dimension];
        }
    }

    private static class RecordingEmbeddingModel implements EmbeddingModel {
        private final int dimension;
        private final List<Integer> batchSizes = new ArrayList<>();

        RecordingEmbeddingModel(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            batchSizes.add(segments.size());
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(new float[dimension]))
                    .toList());
        }
    }
}
