package com.rag.document.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
        DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "hello world".getBytes());
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.store(any(), any())).thenReturn("documents/policy.txt");
        when(parser.parse(any(), any())).thenReturn("hello world");

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
}
