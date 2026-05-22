# Phase 6a：Redis 会话记忆与问题改写 - 设计文档

日期：2026-05-22
阶段：Phase 6a
状态：待实施

## 1. Redis 配置和依赖

### pom.xml 新增依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### application.yml 新增 Redis 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
```

### RedisChatMemoryStore

实现 langchain4j 的 `ChatMemoryStore` 接口，使用 Redis List 存储会话消息：

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties ragProperties;

    @Override
    public List<ChatMessage> getMessages(String sessionId) {
        List<Object> raw = redisTemplate.opsForList().range(key(sessionId), 0, -1);
        if (raw == null) return List.of();
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

## 2. ChatMemory 和问题改写流程

### ChatService 改造

当前 `chat(question)` → 新 `chat(sessionId, question)`，流程：

```
1. 如果 sessionId 为空，生成新 UUID
2. 从 RedisChatMemoryStore 加载历史消息（最近 window-size 轮）
3. 如果有历史 → 用 LLM 改写追问为独立问题
4. 用改写后的问题做向量检索
5. 将历史对话 + 检索结果 + 原始问题拼入回答 prompt
6. LLM 生成回答
7. 保存本轮问答到 ChatMemory（Redis）
8. 保存问答到 MySQL（ChatHistoryEntity 增加 sessionId 字段）
9. 返回 ChatResult + sessionId
```

### 问题改写 prompt

```
根据以下对话历史，将用户的最新问题改写为一个独立、完整的问题。
如果最新问题本身已经完整，则直接返回原问题。

对话历史：
{history}

最新问题：{question}

改写后的独立问题：
```

### 回答 prompt 改造

```
基于以下内容回答问题，结合对话历史理解上下文。
如果内容中没有相关信息，请说明无法回答。

对话历史：
{history}

检索内容：
{context}

问题：{question}

回答：
```

### ChatService 代码结构

```java
@Service
public class ChatService {
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final DocumentRepository documentRepository;
    private final ChatLanguageModel chatModel;
    private final RagProperties ragProperties;
    private final ChatMemoryStore chatMemoryStore;

    public ChatResult chat(String sessionId, String question) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            ChatMemory memory = MessageWindowChatMemory.builder()
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
                saveHistory(sessionId, question, NO_CONTEXT_ANSWER);
                return new ChatResult(NO_CONTEXT_ANSWER, List.of(), sessionId);
            }

            String context = relevantMatches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n\n"));

            String historyText = formatHistory(history);
            String prompt = buildPrompt(context, historyText, question);
            String answer = chatModel.generate(prompt);

            List<SourceReference> sources = ...; // 同现有逻辑

            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(answer));
            saveHistory(sessionId, question, answer);

            return new ChatResult(answer, sources, sessionId);
        } catch (RuntimeException e) {
            throw new IllegalStateException("对话服务暂时不可用，请稍后重试");
        }
    }

    private String rewriteQuestion(List<ChatMessage> history, String question) {
        if (history.isEmpty()) return question;
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
}
```

## 3. API 变化和数据模型

### ChatController API

当前：`POST /api/chat` body: `{ question }`
新增：`POST /api/chat` body: `{ question, sessionId }`

sessionId 可选，前端首次请求不携带时后端自动生成 UUID 返回。

新增：`GET /api/chat/history?sessionId={sessionId}` - 返回当前会话历史消息列表

### ChatHistoryEntity 新增字段

```java
@Column(length = 36)
private String sessionId;
```

### DTO

```java
public record ChatRequest(String question, String sessionId) {}

public record ChatResponse(String answer, List<SourceReference> sources, String sessionId) {}
```

## 4. 前端变化

### chatApi.ts 改造

```typescript
export interface ChatRequest {
  question: string;
  sessionId?: string;
}

export interface ChatResponse {
  answer: string;
  sources: SourceReference[];
  sessionId: string;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  timestamp?: string;
}

export async function sendChat(request: ChatRequest): Promise<ChatResponse> { ... }
export async function getChatHistory(sessionId: string): Promise<ChatMessage[]> { ... }
```

### App.tsx ChatPage 改造

- 新增 sessionId state，初始为 undefined
- 首次发送消息后，从响应获取 sessionId 并保存到 sessionStorage
- 页面加载时，从 sessionStorage 读取 sessionId，如有则调用 getChatHistory 加载历史
- 发送消息时携带 sessionId
- 消息列表从本地 state + Redis 历史合并展示

### 会话重置

新增"新对话"按钮，清空 sessionId 和 sessionStorage，重置消息列表。

## 5. 测试

### 后端测试

| 测试类 | 测试场景 |
|-------|---------|
| RedisChatMemoryStoreTest | getMessages/updateMessages/deleteMessages 基本操作 |
| ChatServiceTest | 首次对话无 sessionId 自动生成 |
| ChatServiceTest | 追问改写：有历史时 LLM 改写追问 |
| ChatServiceTest | 无历史时直接用原问题检索 |
| ChatControllerTest | 请求携带 sessionId 返回相同 sessionId |
| ChatControllerTest | 请求不携带 sessionId 返回新生成的 sessionId |
| ChatControllerTest | GET /api/chat/history 返回历史消息 |

### 前端测试

保持现有 2 个测试通过。

### 验证命令

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend audit --audit-level=high
docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet
```

## 6. 完成标准

- 支持追问和补充条件（如"它有什么功能？"能理解"它"指代上文提到的文档）。
- 会话记忆有 TTL 过期机制。
- 前端展示历史对话，刷新不丢失。
- 检索增强逻辑有可验证测试。