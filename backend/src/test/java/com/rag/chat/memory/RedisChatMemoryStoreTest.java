package com.rag.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RedisChatMemoryStoreTest {

    @Test
    void serializerFormatsUserMessage() {
        String serialized = ChatMessageSerializer.serialize(UserMessage.from("hello"));
        assertEquals("USER:hello", serialized);
    }

    @Test
    void serializerFormatsAiMessage() {
        String serialized = ChatMessageSerializer.serialize(AiMessage.from("answer"));
        assertEquals("AI:answer", serialized);
    }

    @Test
    void deserializerParsesUserMessage() {
        ChatMessage message = ChatMessageDeserializer.deserializeMessage("USER:hello");
        assertInstanceOf(UserMessage.class, message);
        assertEquals("hello", ((UserMessage) message).singleText());
    }

    @Test
    void deserializerParsesAiMessage() {
        ChatMessage message = ChatMessageDeserializer.deserializeMessage("AI:answer");
        assertInstanceOf(AiMessage.class, message);
        assertEquals("answer", ((AiMessage) message).text());
    }

    @Test
    void roundTripPreservesUserMessage() {
        String serialized = ChatMessageSerializer.serialize(UserMessage.from("test question"));
        ChatMessage deserialized = ChatMessageDeserializer.deserializeMessage(serialized);
        assertEquals("test question", ((UserMessage) deserialized).singleText());
    }

    @Test
    void roundTripPreservesAiMessage() {
        String serialized = ChatMessageSerializer.serialize(AiMessage.from("test answer"));
        ChatMessage deserialized = ChatMessageDeserializer.deserializeMessage(serialized);
        assertEquals("test answer", ((AiMessage) deserialized).text());
    }
}