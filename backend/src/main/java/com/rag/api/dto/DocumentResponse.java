package com.rag.api.dto;

import com.rag.document.entity.DocumentEntity;
import java.time.Instant;

public record DocumentResponse(
        String id,
        String name,
        String type,
        String filePath,
        long fileSize,
        String status,
        String errorMessage,
        int chunkCount,
        Instant uploadTime,
        Instant processTime) {

    public static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getName(),
                document.getType(),
                document.getFilePath(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getErrorMessage(),
                document.getChunkCount(),
                document.getUploadTime(),
                document.getProcessTime());
    }
}
