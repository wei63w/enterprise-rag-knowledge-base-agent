package com.rag.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.document.chunk.FixedLengthChunker;
import com.rag.document.entity.ChunkEntity;
import com.rag.document.entity.DocumentEntity;
import com.rag.document.model.DocumentStatus;
import com.rag.document.parser.DocumentTextParser;
import com.rag.document.repository.ChunkRepository;
import com.rag.document.repository.DocumentRepository;
import com.rag.document.storage.StorageService;
import com.rag.embedding.EmbeddingService;
import com.rag.retrieval.BM25KeywordService;
import com.rag.vector.MilvusService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {

    @Test
    void uploadsParsesChunksAndMarksDocumentReady() {
        DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
        StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
        DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
        EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
        MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
        FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
        BM25KeywordService bm25Service = org.mockito.Mockito.mock(BM25KeywordService.class);
        DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService, bm25Service);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "hello world".getBytes());
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.store(any(), any())).thenReturn("documents/policy.txt");
        when(parser.parse(any(), any())).thenReturn("hello world");
        when(embeddingService.embed(any(List.class))).thenReturn(List.of(new float[1024], new float[1024], new float[1024]));

        DocumentEntity result = service.upload(file);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(result.getName()).isEqualTo("policy.txt");
        assertThat(result.getType()).isEqualTo("TXT");
        assertThat(result.getFilePath()).isEqualTo("documents/policy.txt");
        assertThat(result.getChunkCount()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChunkEntity>> chunks = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunks.capture());
        assertThat(chunks.getValue())
                .extracting(ChunkEntity::getContent)
                .containsExactly("hello", " worl", "d");
    }

    @Test
    void deleteThrowsForNonexistentDocument() {
        DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
        StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
        DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
        EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
        MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
        FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
        BM25KeywordService bm25Service = org.mockito.Mockito.mock(BM25KeywordService.class);
        DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService, bm25Service);
        
        when(documentRepository.findById("nonexistent")).thenReturn(java.util.Optional.empty());
        
        assertThrows(IllegalArgumentException.class, () -> service.delete("nonexistent"));
    }

    @Test
    void deleteThrowsForDeletingStatusDocument() {
        DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
        StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
        DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
        EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
        MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
        FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
        BM25KeywordService bm25Service = org.mockito.Mockito.mock(BM25KeywordService.class);
        DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService, bm25Service);
        
        DocumentEntity document = new DocumentEntity("test.txt", "TXT", 100);
        document.markDeleting();
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));
        
        assertThrows(IllegalStateException.class, () -> service.delete("doc-1"));
    }

    @Test
    void deleteMarksDeleteFailedOnMilvusError() {
        DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
        StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
        DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
        EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
        MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
        FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
        BM25KeywordService bm25Service = org.mockito.Mockito.mock(BM25KeywordService.class);
        DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService, bm25Service);
        
        DocumentEntity document = new DocumentEntity("test.txt", "TXT", 100);
        document.markStored("documents/test.txt");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("Milvus error")).when(milvusService).deleteByDocId("doc-1");
        
        assertThrows(IllegalStateException.class, () -> service.delete("doc-1"));
        
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(DocumentStatus.DELETE_FAILED);
    }
}
