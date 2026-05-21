package com.rag.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunks")
public class ChunkEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    private String docId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Instant createdAt = Instant.now();

    protected ChunkEntity() {
    }

    public ChunkEntity(String docId, int chunkIndex, String content) {
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public String getDocId() {
        return docId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
