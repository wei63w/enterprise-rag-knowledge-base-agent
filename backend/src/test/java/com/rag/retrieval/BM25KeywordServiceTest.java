package com.rag.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BM25KeywordServiceTest {

    @Test
    void indexesAndSearchesChunks() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        service.indexChunks("doc-1", List.of("神经网络是一种机器学习方法", "深度学习使用多层神经网络"));
        List<BM25KeywordService.KeywordHit> results = service.search("神经网络", 10);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(h -> h.docId().equals("doc-1")));
    }

    @Test
    void deleteByDocIdRemovesChunks() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        service.indexChunks("doc-1", List.of("内容一"));
        service.deleteByDocId("doc-1");
        List<BM25KeywordService.KeywordHit> results = service.search("内容一", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchReturnsEmptyForNoMatch() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        List<BM25KeywordService.KeywordHit> results = service.search("不存在的关键词", 10);

        assertNotNull(results);
    }

    @Test
    void keywordHitHoldsAllFields() {
        BM25KeywordService.KeywordHit hit = new BM25KeywordService.KeywordHit("doc-1", "content", 0.85);

        assertEquals("doc-1", hit.docId());
        assertEquals("content", hit.content());
        assertEquals(0.85, hit.score());
    }
}
