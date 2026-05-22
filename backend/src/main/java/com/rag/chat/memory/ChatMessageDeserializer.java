package com.rag.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

public class ChatMessageDeserializer {

    public static ChatMessage deserializeMessage(String raw) {
        if (raw.startsWith("USER:")) {
            return UserMessage.from(raw.substring(5));
        }
        if (raw.startsWith("AI:")) {
            return AiMessage.from(raw.substring(3));
        }
        if (raw.startsWith("SYSTEM:")) {
            return SystemMessage.from(raw.substring(7));
        }
        return UserMessage.from(raw);
    }
}