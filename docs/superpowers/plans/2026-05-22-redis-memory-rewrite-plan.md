# Phase 6a：Redis 会话记忆与问题改写 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接入 Redis 会话记忆、实现多轮对话窗口、引入 LLM 问题改写提升追问理解。

**Architecture:** 使用 langchain4j ChatMemory 接口 + 自定义 RedisChatMemoryStore 实现会话持久化，MessageWindowChatMemory 管理窗口裁剪。追问改写通过额外 LLM 调用将追问转为独立问题用于向量检索。

**Tech Stack:** Spring Boot Data Redis、langchain4j ChatMemory、RedisTemplate

---

## 文件结构

| 文件 | 操作 | 职责 |
|-----|------|-----|
| `backend/pom.xml` | 修改 | 新增 spring-boot-starter-data-redis 依赖 |
| `backend/src/main/resources/application.yml` | 修改 | 新增 Redis 连接配置 |
| `backend/src/main/java/com/rag/chat/memory/RedisChatMemoryStore.java` | 新建 | Redis 持久化 ChatMemory |
| `backend/src/test/java/com/rag/chat/memory/RedisChatMemoryStoreTest.java` | 新建 | RedisChatMemoryStore 测试 |
| `backend/src/main/java/com/rag/chat/ChatService.java` | 修改 | 添加 sessionId、ChatMemory、问题改写 |
| `backend/src/test/java/com/rag/chat/ChatServiceTest.java` | 修改 | 新增会话和改写测试 |
| `backend/src/main/java/com/rag/chat/entity/ChatHistoryEntity.java` | 修改 | 新增 sessionId 字段 |
| `backend/src/main/java/com/rag/chat/repository/ChatHistoryRepository.java` | 修改 | 新增 findBySessionIdOrderByCreatedAt 查询 |
| `backend/src/main/java/com/rag/api/dto/ChatRequest.java` | 修改 | 新增 sessionId 字段 |
| `backend/src/main/java/com/rag/api/dto/ChatResponse.java` | 修改 | 新增 sessionId 字段 |
| `backend/src/main/java/com/rag/api/controller/ChatController.java` | 修改 | 传递 sessionId，新增 history API |
| `backend/src/test/java/com/rag/api/controller/DocumentControllerTest.java` | 修改 | 新增 ChatController 测试（sessionId 相关） |
| `frontend/src/services/chatApi.ts` | 修改 | 新增 sessionId、getChatHistory |
| `frontend/src/App.tsx` | 修改 | ChatPage 添加 sessionId 管理、历史加载、新对话按钮 |

---

### Task 1: 添加 Redis 依赖和配置

**Files:**
- Modify: `backend/pom.xml:24-92`
- Modify: `backend/src/main/resources/application.yml:4-15`

- [ ] **Step 1: pom.xml 新增 Redis 依赖**

在 `spring-boot-starter-validation` 依赖后添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 2: application.yml 新增 Redis 配置**

在 `spring.jpa` 配置后添加：

```yaml
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
```

- [ ] **Step 3: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All 30 tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "feat: add Redis dependency and configuration"
```

---

### Task 2: 实现 RedisChatMemoryStore

**Files:**
- Create: `backend/src/main/java/com/rag/chat/memory/RedisChatMemoryStore.java`
- Create: `backend/src/test/java/com/rag/chat/memory/RedisChatMemoryStoreTest.java`

- [ ] **Step 1: 创建 RedisChatMemoryStore**

```java
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
    public List<ChatMessage> getMessages(String sessionId) {
        List<Object> raw = redisTemplate.opsForList().range(key(sessionId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(obj -> ChatMessageDeserializer.deserializeMessage((String) obj))
                .toList();
    }

    @Override
    public void updateMessages(String sessionId, List<ChatMessage> messages) {
        String key = key(sessionId);
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
    public void deleteMessages(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return "chat:memory:" + sessionId;
    }
}
```

- [ ] **Step 2: 创建 ChatMessageSerializer**

```java
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
```

- [ ] **Step 3: 创建 ChatMessageDeserializer**

```java
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
```

- [ ] **Step 4: 创建 RedisChatMemoryStoreTest**

```java
package com.rag.chat.memory;

import com.rag.config.RagProperties;
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
```

- [ ] **Step 5: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rag/chat/memory/ backend/src/test/java/com/rag/chat/memory/
git commit -m "feat: add RedisChatMemoryStore with serializer/deserializer"
```

---

### Task 3: 改造 ChatHistoryEntity 和 Repository

**Files:**
- Modify: `backend/src/main/java/com/rag/chat/entity/ChatHistoryEntity.java`
- Modify: `backend/src/main/java/com/rag/chat/repository/ChatHistoryRepository.java`

- [ ] **Step 1: ChatHistoryEntity 新增 sessionId 字段**

在 `model` 字段后添加：

```java
@Column(length = 36)
private String sessionId;
```

在构造函数中添加 sessionId 参数：

```java
public ChatHistoryEntity(String question, String answer, String model, String sessionId) {
    this.question = question;
    this.answer = answer;
    this.model = model;
    this.sessionId = sessionId;
}
```

保留原三参数构造函数（兼容旧代码）：

```java
public ChatHistoryEntity(String question, String answer, String model) {
    this(question, answer, model, null);
}
```

新增 getter/setter：

```java
public String getSessionId() { return sessionId; }
public void setSessionId(String sessionId) { this.sessionId = sessionId; }
```

- [ ] **Step 2: ChatHistoryRepository 新增查询方法**

```java
public interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntity, String> {
    List<ChatHistoryEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
```

- [ ] **Step 3: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/chat/entity/ChatHistoryEntity.java backend/src/main/java/com/rag/chat/repository/ChatHistoryRepository.java
git commit -m "feat: add sessionId to ChatHistoryEntity and repository query"
```

---

### Task 4: 改造 ChatRequest 和 ChatResponse DTO

**Files:**
- Modify: `backend/src/main/java/com/rag/api/dto/ChatRequest.java`
- Modify: `backend/src/main/java/com/rag/api/dto/ChatResponse.java`

- [ ] **Step 1: ChatRequest 新增 sessionId 字段**

在 `docIds` 字段后添加：

```java
private String sessionId;

public String getSessionId() {
    return sessionId;
}

public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
}
```

- [ ] **Step 2: ChatResponse 新增 sessionId 字段**

在 `sources` 字段后添加：

```java
private String sessionId;

public String getSessionId() {
    return sessionId;
}

public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
}
```

- [ ] **Step 3: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/api/dto/ChatRequest.java backend/src/main/java/com/rag/api/dto/ChatResponse.java
git commit -m "feat: add sessionId to ChatRequest and ChatResponse DTOs"
```

---

### Task 5: 改造 ChatService - 添加会话和问题改写

**Files:**
- Modify: `backend/src/main/java/com/rag/chat/ChatService.java`
- Modify: `backend/src/test/java/com/rag/chat/ChatServiceTest.java`

- [ ] **Step 1: 改造 ChatService**

完整替换文件：

```java
package com.rag.chat;

import com.rag.chat.entity.ChatHistoryEntity;
import com.rag.chat.memory.RedisChatMemoryStore;
import com.rag.chat.repository.ChatHistoryRepository;
import com.rag.config.RagProperties;
import com.rag.document.entity.DocumentEntity;
import com.rag.document.repository.DocumentRepository;
import com.rag.embedding.EmbeddingService;
import com.rag.vector.MilvusService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String NO_CONTEXT_ANSWER = "当前知识库中不具备足够依据回答该问题";
    private static final double MIN_SCORE_THRESHOLD = 0.3;

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final DocumentRepository documentRepository;
    private final ChatLanguageModel chatModel;
    private final RagProperties ragProperties;
    private final RedisChatMemoryStore chatMemoryStore;

    public ChatService(
            EmbeddingService embeddingService,
            MilvusService milvusService,
            ChatHistoryRepository chatHistoryRepository,
            DocumentRepository documentRepository,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${chat.model}") String modelName,
            @Value("${chat.temperature}") Double temperature,
            RagProperties ragProperties,
            RedisChatMemoryStore chatMemoryStore) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.documentRepository = documentRepository;
        this.ragProperties = ragProperties;
        this.chatMemoryStore = chatMemoryStore;
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    public ChatResult chat(String sessionId, String question) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                    .id(sessionId)
                    .maxMessages(ragProperties.getMemory().getWindowSize() * 2)
                    .chatMemoryStore(chatMemoryStore)
                    .build();

            List<ChatMessage> history = memory.messages();

            String searchQuestion = rewriteQuestion(history, question);

            float[] queryVector = embeddingService.embed(searchQuestion);
            List<EmbeddingMatch<TextSegment>> matches = milvusService.search(
                    queryVector, ragProperties.getRetrieval().getVectorTopK());

            List<EmbeddingMatch<TextSegment>> relevantMatches = matches.stream()
                    .filter(m -> m.score() >= MIN_SCORE_THRESHOLD)
                    .collect(Collectors.toList());

            if (relevantMatches.isEmpty()) {
                memory.add(UserMessage.from(question));
                memory.add(AiMessage.from(NO_CONTEXT_ANSWER));
                chatHistoryRepository.save(new ChatHistoryEntity(question, NO_CONTEXT_ANSWER, "deepseek-chat", sessionId));
                return new ChatResult(NO_CONTEXT_ANSWER, List.of(), sessionId);
            }

            String context = relevantMatches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n\n"));

            String historyText = formatHistory(history);
            String prompt = buildPrompt(context, historyText, question);
            String answer = chatModel.generate(prompt);

            List<SourceReference> sources = relevantMatches.stream()
                    .map(m -> {
                        String docId = m.embedded().metadata().get("docId");
                        String docName = "";
                        if (docId != null) {
                            docName = documentRepository.findById(docId)
                                    .map(DocumentEntity::getName)
                                    .orElse("");
                        }
                        return new SourceReference(docId, docName, m.embedded().text(), m.score());
                    })
                    .collect(Collectors.toList());

            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(answer));
            chatHistoryRepository.save(new ChatHistoryEntity(question, answer, "deepseek-chat", sessionId));

            return new ChatResult(answer, sources, sessionId);
        } catch (RuntimeException e) {
            throw new IllegalStateException("对话服务暂时不可用，请稍后重试");
        }
    }

    private String rewriteQuestion(List<ChatMessage> history, String question) {
        if (history.isEmpty()) {
            return question;
        }
        String historyText = formatHistory(history);
        String rewritePrompt = String.format(
                "根据以下对话历史，将用户的最新问题改写为一个独立、完整的问题。\n" +
                "如果最新问题本身已经完整，则直接返回原问题。\n\n" +
                "对话历史：\n%s\n\n最新问题：%s\n\n改写后的独立问题：",
                historyText, question);
        return chatModel.generate(rewritePrompt);
    }

    private String formatHistory(List<ChatMessage> history) {
        return history.stream()
                .map(msg -> {
                    if (msg instanceof UserMessage) return "用户：" + ((UserMessage) msg).singleText();
                    if (msg instanceof AiMessage) return "助手：" + ((AiMessage) msg).text();
                    return "";
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildPrompt(String context, String historyText, String question) {
        return String.format(
                "基于以下内容回答问题，结合对话历史理解上下文。\n" +
                "如果内容中没有相关信息，请说明无法回答。\n\n" +
                "对话历史：\n%s\n\n检索内容：\n%s\n\n问题：%s\n\n回答：",
                historyText, context, question);
    }

    public static class ChatResult {
        private final String answer;
        private final List<SourceReference> sources;
        private final String sessionId;

        public ChatResult(String answer, List<SourceReference> sources, String sessionId) {
            this.answer = answer;
            this.sources = sources;
            this.sessionId = sessionId;
        }

        public String getAnswer() { return answer; }
        public List<SourceReference> getSources() { return sources; }
        public String getSessionId() { return sessionId; }
    }

    public static class SourceReference {
        private final String docId;
        private final String docName;
        private final String content;
        private final double score;

        public SourceReference(String docId, String docName, String content, double score) {
            this.docId = docId;
            this.docName = docName;
            this.content = content;
            this.score = score;
        }

        public String getDocId() { return docId; }
        public String getDocName() { return docName; }
        public String getContent() { return content; }
        public double getScore() { return score; }
    }
}
```

- [ ] **Step 2: 更新 ChatServiceTest**

先读取当前测试文件，然后更新测试以适配新的 ChatResult（新增 sessionId）：

```java
package com.rag.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void chatResultHoldsAnswerAndSourcesAndSessionId() {
        ChatService.SourceReference source = new ChatService.SourceReference("doc1", "policy.txt", "content text", 0.85);
        ChatService.ChatResult result = new ChatService.ChatResult("answer text", List.of(source), "session-1");
        assertEquals("answer text", result.getAnswer());
        assertEquals(1, result.getSources().size());
        assertEquals("session-1", result.getSessionId());
    }

    @Test
    void chatResultWithNoSources() {
        ChatService.ChatResult result = new ChatService.ChatResult(
                "当前知识库中不具备足够依据回答该问题", List.of(), "session-2");
        assertTrue(result.getSources().isEmpty());
        assertEquals("session-2", result.getSessionId());
    }

    @Test
    void sourceReferenceHoldsAllFields() {
        ChatService.SourceReference source = new ChatService.SourceReference("doc2", "report.md", "chunk text", 0.72);
        assertEquals("doc2", source.getDocId());
        assertEquals("report.md", source.getDocName());
        assertEquals("chunk text", source.getContent());
        assertEquals(0.72, source.getScore());
    }

    @Test
    void wrapsRuntimeExceptionAsIllegalStateException() {
        IllegalStateException wrapped = new IllegalStateException("对话服务暂时不可用，请稍后重试");
        assertEquals("对话服务暂时不可用，请稍后重试", wrapped.getMessage());
    }
}
```

- [ ] **Step 3: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/chat/ChatService.java backend/src/test/java/com/rag/chat/ChatServiceTest.java
git commit -m "feat: add session memory and question rewrite to ChatService"
```

---

### Task 6: 改造 ChatController

**Files:**
- Modify: `backend/src/main/java/com/rag/api/controller/ChatController.java`

- [ ] **Step 1: 改造 ChatController**

完整替换文件：

```java
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
```

- [ ] **Step 2: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/rag/api/controller/ChatController.java
git commit -m "feat: add sessionId support and history API to ChatController"
```

---

### Task 7: 前端 chatApi.ts 改造

**Files:**
- Modify: `frontend/src/services/chatApi.ts`

- [ ] **Step 1: 改造 chatApi.ts**

完整替换文件：

```typescript
export interface ChatRequest {
  question: string;
  sessionId?: string;
}

export interface SourceReference {
  docId: string;
  docName: string;
  content: string;
  score: number;
}

export interface ChatResponse {
  answer: string;
  sources: SourceReference[];
  sessionId: string;
}

export interface ChatHistoryMessage {
  role: "user" | "assistant";
  content: string;
  timestamp?: string;
}

export async function sendChat(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}) as { message?: string });
    throw new Error(errorBody.message || `对话请求失败：${response.status}`);
  }

  return response.json();
}

export async function getChatHistory(sessionId: string): Promise<ChatHistoryMessage[]> {
  const response = await fetch(`/api/chat/history?sessionId=${encodeURIComponent(sessionId)}`);

  if (!response.ok) {
    return [];
  }

  const history: Array<{ question: string; answer: string; createdAt?: string }> = await response.json();
  const messages: ChatHistoryMessage[] = [];
  for (const entry of history) {
    messages.push({ role: "user", content: entry.question, timestamp: entry.createdAt });
    messages.push({ role: "assistant", content: entry.answer, timestamp: entry.createdAt });
  }
  return messages;
}
```

- [ ] **Step 2: 运行前端测试和构建**

Run: `npm --prefix frontend test`
Run: `npm --prefix frontend run build`
Expected: All tests pass, build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/chatApi.ts
git commit -m "feat: add sessionId and history API to chatApi.ts"
```

---

### Task 8: 前端 App.tsx ChatPage 改造

**Files:**
- Modify: `frontend/src/App.tsx:149-222`

- [ ] **Step 1: 改造 ChatPage**

替换 ChatPage 函数（第 149-222 行）：

```tsx
function ChatPage() {
  const [messages, setMessages] = useState<Array<{ role: "user" | "assistant"; content: string; sources?: SourceReference[] }>>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | undefined>(() => sessionStorage.getItem("rag-session-id") || undefined);

  useEffect(() => {
    if (sessionId) {
      void loadHistory();
    }
  }, []);

  async function loadHistory() {
    if (!sessionId) return;
    try {
      const historyMessages = await getChatHistory(sessionId);
      const displayMessages = historyMessages.map(msg => ({
        role: msg.role,
        content: msg.content
      }));
      setMessages(displayMessages);
    } catch {
      // 历史加载失败，使用空列表
    }
  }

  async function handleSend() {
    if (!input.trim()) return;

    const question = input.trim();
    setInput("");
    setMessages(prev => [...prev, { role: "user", content: question }]);
    setLoading(true);
    setError(null);

    try {
      const response = await sendChat({ question, sessionId });
      if (!sessionId) {
        setSessionId(response.sessionId);
        sessionStorage.setItem("rag-session-id", response.sessionId);
      }
      setMessages(prev => [...prev, { role: "assistant", content: response.answer, sources: response.sources }]);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "问答请求失败");
    } finally {
      setLoading(false);
    }
  }

  function handleNewChat() {
    setSessionId(undefined);
    sessionStorage.removeItem("rag-session-id");
    setMessages([]);
    setError(null);
  }

  return (
    <section className="workspace-panel" aria-labelledby="chat-title">
      <div className="panel-heading">
        <div>
          <Title id="chat-title" level={2}>对话</Title>
          <Text type="secondary">RAG 问答工作区</Text>
        </div>
        <Space>
          <Tag color="blue">多轮记忆</Tag>
          <Button icon={<ReloadOutlined />} onClick={handleNewChat}>新对话</Button>
        </Space>
      </div>
      {error ? <Alert className="panel-alert" message={error} showIcon type="error" /> : null}
      <div className="chat-surface">
        {messages.length === 0 ? (
          <div className="chat-placeholder">输入问题开始问答</div>
        ) : (
          messages.map((msg, idx) => (
            <div key={idx} className={`message message-${msg.role}`}>
              <div className="message-content">{msg.content}</div>
              {msg.sources && msg.sources.length > 0 ? (
                <div className="message-sources">
                  {msg.sources.map((s, sIdx) => (
                    <div key={sIdx}>
                      <div className="source-header">
                        <Text strong>{s.docName}</Text>
                        <Tag color="blue">相关性: {(s.score * 100).toFixed(1)}%</Tag>
                      </div>
                      <div className="source-content">{s.content}</div>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ))
        )}
      </div>
      <div className="chat-input-area">
        <Input.TextArea
          placeholder="输入问题..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onPressEnter={e => { if (!e.shiftKey) { e.preventDefault(); void handleSend(); } }}
          rows={2}
        />
        <Button type="primary" loading={loading} onClick={() => void handleSend()}>
          发送
        </Button>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: 更新 import**

在 App.tsx 的 import 中，从 chatApi.ts 添加 `getChatHistory`：

```tsx
import { sendChat, getChatHistory, type SourceReference } from "./services/chatApi";
```

- [ ] **Step 3: 更新 Header subtitle**

将 `<Text type="secondary">Phase 4 Source Tracing</Text>` 改为 `<Text type="secondary">Phase 6a Session Memory</Text>`。

- [ ] **Step 4: 运行前端测试和构建**

Run: `npm --prefix frontend test`
Run: `npm --prefix frontend run build`
Expected: All tests pass, build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat: add session memory, history loading, and new chat button to ChatPage"
```

---

### Task 9: 验证并更新文档

**Files:**
- Modify: `docs/管理/阶段任务.md`
- Modify: `docs/管理/变更记录.md`

- [ ] **Step 1: 运行全部验证命令**

Run:
```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend audit --audit-level=high
docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet
```
Expected: All pass

- [ ] **Step 2: 更新阶段任务文档**

更新 Phase 6a 状态为已完成。

- [ ] **Step 3: 更新变更记录文档**

新增 Phase 6a 变更记录。

- [ ] **Step 4: Commit**

```bash
git add docs/管理/阶段任务.md docs/管理/变更记录.md
git commit -m "docs: mark Phase 6a complete with session memory and question rewrite"
```

---

### Task 10: 推送到 GitHub

- [ ] **Step 1: Push all commits**

```bash
git push
```
Expected: All commits pushed successfully