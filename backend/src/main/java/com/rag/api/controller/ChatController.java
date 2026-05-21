package com.rag.api.controller;

import com.rag.api.dto.ChatRequest;
import com.rag.api.dto.ChatResponse;
import com.rag.api.dto.SourceReference;
import com.rag.chat.ChatService;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatService.ChatResult result = chatService.chat(request.getQuestion());
        ChatResponse response = new ChatResponse();
        response.setAnswer(result.getAnswer());
        response.setSources(result.getSources().stream()
                .map(s -> new SourceReference(s.getContent(), s.getScore()))
                .collect(Collectors.toList()));
        return response;
    }
}