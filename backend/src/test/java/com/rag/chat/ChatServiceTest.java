package com.rag.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void chatResultHoldsAnswerAndSources() {
        ChatService.SourceReference source = new ChatService.SourceReference("content text", 0.85);
        ChatService.ChatResult result = new ChatService.ChatResult("answer text", List.of(source));
        assertEquals("answer text", result.getAnswer());
        assertEquals(1, result.getSources().size());
        assertEquals("content text", result.getSources().get(0).getContent());
        assertEquals(0.85, result.getSources().get(0).getScore());
    }

    @Test
    void chatResultWithNoSources() {
        ChatService.ChatResult result = new ChatService.ChatResult(
                "当前知识库中不具备足够依据回答该问题", List.of());
        assertTrue(result.getSources().isEmpty());
    }

    @Test
    void sourceReferenceHoldsContentAndScore() {
        ChatService.SourceReference source = new ChatService.SourceReference("chunk text", 0.72);
        assertEquals("chunk text", source.getContent());
        assertEquals(0.72, source.getScore());
    }
}