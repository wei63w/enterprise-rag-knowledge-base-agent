package com.rag.api.dto;

public class ChunkResponse {
    private String id;
    private String docId;
    private int chunkIndex;
    private String content;

    public ChunkResponse() {}

    public ChunkResponse(String id, String docId, int chunkIndex, String content) {
        this.id = id;
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}