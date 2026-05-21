package com.rag.api.dto;

public class SourceReference {
    private String content;
    private double score;

    public SourceReference() {}

    public SourceReference(String content, double score) {
        this.content = content;
        this.score = score;
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