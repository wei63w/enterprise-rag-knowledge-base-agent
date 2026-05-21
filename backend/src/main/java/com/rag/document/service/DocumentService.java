package com.rag.document.service;

import com.rag.document.chunk.FixedLengthChunker;
import com.rag.document.entity.ChunkEntity;
import com.rag.document.entity.DocumentEntity;
import com.rag.document.model.DocumentStatus;
import com.rag.document.parser.DocumentTextParser;
import com.rag.document.repository.ChunkRepository;
import com.rag.document.repository.DocumentRepository;
import com.rag.document.storage.StorageService;
import com.rag.document.storage.StorageService.StoredObject;
import com.rag.embedding.EmbeddingService;
import com.rag.vector.MilvusService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final List<String> SUPPORTED_TYPES = List.of("PDF", "MD", "TXT");

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final StorageService storageService;
    private final DocumentTextParser parser;
    private final FixedLengthChunker chunker;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;

    public DocumentService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            StorageService storageService,
            DocumentTextParser parser,
            FixedLengthChunker chunker,
            EmbeddingService embeddingService,
            MilvusService milvusService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
    }

    @Transactional
    public DocumentEntity upload(MultipartFile file) {
        validateFile(file);
        String filename = safeFilename(file);
        String type = documentType(filename);
        DocumentEntity document = documentRepository.save(new DocumentEntity(filename, type, file.getSize()));

        try {
            String objectKey = storageService.store(file.getInputStream(), new StoredObject(
                    filename,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    file.getSize()));
            document.markStored(objectKey);
            String text = parser.parse(file.getInputStream(), filename);
            List<String> chunkTexts = chunker.split(text);
            if (chunkTexts.isEmpty()) {
                throw new IllegalStateException("未提取到可索引文本");
            }
            List<ChunkEntity> chunks = toChunks(document.getId(), chunkTexts);
            chunkRepository.saveAll(chunks);
            document.markReady(chunks.size());
            documentRepository.save(document);
            List<String> contents = chunks.stream().map(ChunkEntity::getContent).toList();
            List<String> docIds = chunks.stream().map(c -> c.getDocId()).toList();
            List<float[]> embeddings = embeddingService.embed(contents);
            milvusService.insert(docIds, contents, embeddings);
            return document;
        } catch (IOException e) {
            document.markError("文件读取失败");
            return documentRepository.save(document);
        } catch (RuntimeException e) {
            document.markError(e.getMessage());
            return documentRepository.save(document);
        }
    }

    public List<DocumentEntity> list() {
        return documentRepository.findAll();
    }

    public DocumentEntity get(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + id));
    }

    @Transactional
    public void delete(String id) {
        DocumentEntity document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + id));
        
        if (document.getStatus() == DocumentStatus.DELETING) {
            throw new IllegalStateException("文档正在删除中，请勿重复操作");
        }
        
        document.markDeleting();
        documentRepository.save(document);
        
        try {
            milvusService.deleteByDocId(id);
            
            if (document.getFilePath() != null && !document.getFilePath().isEmpty()) {
                storageService.delete(document.getFilePath());
            }
            
            chunkRepository.deleteByDocId(id);
            documentRepository.delete(document);
        } catch (Exception e) {
            document.markDeleteFailed("删除失败：" + e.getMessage());
            documentRepository.save(document);
            throw new IllegalStateException("删除失败：" + e.getMessage());
        }
    }

    private List<ChunkEntity> toChunks(String docId, List<String> chunkTexts) {
        return java.util.stream.IntStream.range(0, chunkTexts.size())
                .mapToObj(index -> new ChunkEntity(docId, index, chunkTexts.get(index)))
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String type = documentType(safeFilename(file));
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的文件格式：" + type);
        }
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return filename;
    }

    private String documentType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "UNKNOWN";
        }
        return filename.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
