package com.rag.embedding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

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
}