package com.rag.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

public class ChatMessageSerializer {

    public static String serialize(ChatMessage message) {
        if (message instanceof UserMessage) {
            return "USER:" + ((UserMessage) message).singleText();
        }
        if (message instanceof AiMessage) {
            return "AI:" + ((AiMessage) message).text();
        }
        if (message instanceof SystemMessage) {
            return "SYSTEM:" + ((SystemMessage) message).text();
        }
        return "UNKNOWN:" + message.toString();
    }
}