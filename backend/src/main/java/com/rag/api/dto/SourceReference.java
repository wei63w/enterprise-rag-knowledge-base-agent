package com.rag.api.dto;

public class SourceReference {
    private String docId;
    private String docName;
    private String content;
    private double score;

    public SourceReference() {}

    public SourceReference(String docId, String docName, String content, double score) {
        this.docId = docId;
        this.docName = docName;
        this.content = content;
        this.score = score;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}