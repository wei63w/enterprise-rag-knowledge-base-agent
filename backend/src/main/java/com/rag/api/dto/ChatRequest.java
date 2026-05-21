package com.rag.api.dto;

import java.util.List;

public class ChatRequest {
    private String question;
    private List<String> docIds;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getDocIds() {
        return docIds;
    }

    public void setDocIds(List<String> docIds) {
        this.docIds = docIds;
    }
}