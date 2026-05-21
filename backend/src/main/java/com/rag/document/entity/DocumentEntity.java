package com.rag.document.entity;

import com.rag.document.model.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private String filePath;

    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PROCESSING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private int chunkCount;

    @Column(nullable = false)
    private Instant uploadTime = Instant.now();

    private Instant processTime;

    protected DocumentEntity() {
    }

    public DocumentEntity(String name, String type, long fileSize) {
        this.name = name;
        this.type = type;
        this.fileSize = fileSize;
        this.filePath = "";
    }

    public void markStored(String filePath) {
        this.filePath = filePath;
    }

    public void markReady(int chunkCount) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
        this.processTime = Instant.now();
    }

    public void markError(String message) {
        this.status = DocumentStatus.ERROR;
        this.errorMessage = message;
        this.processTime = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getUploadTime() {
        return uploadTime;
    }

    public Instant getProcessTime() {
        return processTime;
    }
}
