package com.rag.chat.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_history")
public class ChatHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(length = 50)
    private String model;

    @Column(length = 36)
    private String sessionId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatHistoryEntity() {}

    public ChatHistoryEntity(String question, String answer, String model, String sessionId) {
        this.question = question;
        this.answer = answer;
        this.model = model;
        this.sessionId = sessionId;
    }

    public ChatHistoryEntity(String question, String answer, String model) {
        this(question, answer, model, null);
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}