package com.rag.vector;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MilvusServiceTest {

    @Test
    void milvusPropertiesDefaultsAreSet() {
        MilvusProperties props = new MilvusProperties();
        assertEquals("localhost", props.getHost());
        assertEquals(19530, props.getPort());
        assertEquals("document_chunks_v1", props.getCollection());
        assertEquals(1024, props.getDimension());
    }

    @Test
    void milvusPropertiesCanBeCustomized() {
        MilvusProperties props = new MilvusProperties();
        props.setHost("milvus-server");
        props.setPort(19531);
        props.setCollection("custom_chunks");
        props.setDimension(768);
        assertEquals("milvus-server", props.getHost());
        assertEquals(19531, props.getPort());
        assertEquals("custom_chunks", props.getCollection());
        assertEquals(768, props.getDimension());
    }

    @Test
    void searchResultRecordHasCorrectFields() {
        SearchResult result = new SearchResult("doc1", 3, "content text", 0.85);
        assertEquals("doc1", result.docId);
        assertEquals(3, result.chunkIndex);
        assertEquals("content text", result.content);
        assertEquals(0.85, result.score);
    }

    private static class SearchResult {
        final String docId;
        final int chunkIndex;
        final String content;
        final double score;

        SearchResult(String docId, int chunkIndex, String content, double score) {
            this.docId = docId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.score = score;
        }
    }
}