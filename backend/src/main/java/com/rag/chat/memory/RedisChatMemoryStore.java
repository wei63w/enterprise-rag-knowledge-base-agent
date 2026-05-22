package com.rag.chat.memory;

import com.rag.config.RagProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties ragProperties;

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate, RagProperties ragProperties) {
        this.redisTemplate = redisTemplate;
        this.ragProperties = ragProperties;
    }

    @Override
    public List<ChatMessage> getMessages(Object sessionId) {
        String id = sessionId.toString();
        List<Object> raw = redisTemplate.opsForList().range(key(id), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(obj -> ChatMessageDeserializer.deserializeMessage((String) obj))
                .toList();
    }

    @Override
    public void updateMessages(Object sessionId, List<ChatMessage> messages) {
        String id = sessionId.toString();
        String key = key(id);
        redisTemplate.delete(key);
        if (!messages.isEmpty()) {
            List<String> serialized = messages.stream()
                    .map(ChatMessageSerializer::serialize)
                    .toList();
            redisTemplate.opsForList().rightPushAll(key, serialized.toArray());
            redisTemplate.expire(key, Duration.ofSeconds(ragProperties.getMemory().getSessionTtlSeconds()));
        }
    }

    @Override
    public void deleteMessages(Object sessionId) {
        redisTemplate.delete(key(sessionId.toString()));
    }

    private String key(String sessionId) {
        return "chat:memory:" + sessionId;
    }
}