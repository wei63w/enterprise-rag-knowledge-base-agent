package com.rag.api.controller;

import com.rag.api.dto.DocumentResponse;
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

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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
}
