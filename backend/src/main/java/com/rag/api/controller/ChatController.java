package com.rag.api.controller;

import com.rag.api.dto.ChatRequest;
import com.rag.api.dto.ChatResponse;
import com.rag.api.dto.SourceReference;
import com.rag.chat.ChatService;
import com.rag.chat.entity.ChatHistoryEntity;
import com.rag.chat.repository.ChatHistoryRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryRepository chatHistoryRepository;

    public ChatController(ChatService chatService, ChatHistoryRepository chatHistoryRepository) {
        this.chatService = chatService;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatService.ChatResult result = chatService.chat(request.getSessionId(), request.getQuestion());
        ChatResponse response = new ChatResponse();
        response.setAnswer(result.getAnswer());
        response.setSessionId(result.getSessionId());
        response.setSources(result.getSources().stream()
                .map(s -> new SourceReference(s.getDocId(), s.getDocName(), s.getContent(), s.getScore()))
                .collect(Collectors.toList()));
        return response;
    }

    @GetMapping("/history")
    public List<ChatHistoryEntity> history(@RequestParam String sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}