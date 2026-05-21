package com.rag.api.controller;

import com.rag.api.dto.ChunkResponse;
import com.rag.api.dto.DocumentResponse;
import com.rag.document.entity.ChunkEntity;
import com.rag.document.repository.ChunkRepository;
import com.rag.document.service.DocumentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ChunkRepository chunkRepository;

    public DocumentController(DocumentService documentService, ChunkRepository chunkRepository) {
        this.documentService = documentService;
        this.chunkRepository = chunkRepository;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @PostMapping("/upload")
    public DocumentResponse upload(@RequestPart("file") MultipartFile file) {
        return DocumentResponse.from(documentService.upload(file));
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable String id) {
        return DocumentResponse.from(documentService.get(id));
    }

    @GetMapping("/{id}/status")
    public DocumentResponse status(@PathVariable String id) {
        return DocumentResponse.from(documentService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/chunks")
    public List<ChunkResponse> chunks(@PathVariable String id) {
        return chunkRepository.findByDocIdOrderByChunkIndexAsc(id).stream()
                .map(c -> new ChunkResponse(c.getId(), c.getDocId(), c.getChunkIndex(), c.getContent()))
                .toList();
    }
}
