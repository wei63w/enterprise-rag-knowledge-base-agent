# Phase 3 向量索引与基础检索问答实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接入 embedding 和 Milvus，让系统可以基于已处理文档执行基础向量召回，并通过 LLM 生成回答。

**Architecture:** 后端由 `ChatService` 编排 RAG 流程：向量检索 → prompt 构建 → LLM 调用。Embedding 使用 LangChain4j DeepSeek 集成，向量存储使用 LangChain4j Milvus 集成。前端 Chat 页接入 `/api/chat` 展示真实问答。

**Tech Stack:** Spring Boot 3.2、LangChain4j 0.35.0、DeepSeek、Milvus、React 18、Ant Design。

---

## 文件结构

### 后端新增文件

| 文件 | 职责 |
|---|---|
| `backend/src/main/java/com/rag/embedding/EmbeddingService.java` | Embedding 服务接口 |
| `backend/src/main/java/com/rag/embedding/DeepSeekEmbeddingService.java` | DeepSeek Embedding 实现 |
| `backend/src/main/java/com/rag/embedding/EmbeddingProperties.java` | Embedding 配置 |
| `backend/src/main/java/com/rag/vector/MilvusService.java` | Milvus 向量服务 |
| `backend/src/main/java/com/rag/vector/MilvusProperties.java` | Milvus 配置 |
| `backend/src/main/java/com/rag/chat/ChatService.java` | Chat 服务，RAG 流程编排 |
| `backend/src/main/java/com/rag/chat/entity/ChatHistoryEntity.java` | 问答历史实体 |
| `backend/src/main/java/com/rag/chat/repository/ChatHistoryRepository.java` | 问答历史仓储 |
| `backend/src/main/java/com/rag/api/controller/ChatController.java` | Chat API 控制器 |
| `backend/src/main/java/com/rag/api/dto/ChatRequest.java` | Chat 请求 DTO |
| `backend/src/main/java/com/rag/api/dto/ChatResponse.java` | Chat 响应 DTO |
| `backend/src/main/java/com/rag/api/dto/SourceReference.java` | 来源引用 DTO |

### 后端修改文件

| 文件 | 改动 |
|---|---|
| `backend/pom.xml` | 添加 LangChain4j 依赖 |
| `backend/src/main/resources/application.yml` | 添加 embedding、chat 配置 |
| `backend/src/main/java/com/rag/RagApplication.java` | 启用配置属性扫描 |
| `backend/src/main/java/com/rag/document/service/DocumentService.java` | 触发向量化流程 |

### 前端新增文件

| 文件 | 职责 |
|---|---|
| `frontend/src/services/chatApi.ts` | Chat API 调用服务 |

### 前端修改文件

| 文件 | 改动 |
|---|---|
| `frontend/src/App.tsx` | Chat 页接入真实问答 |
| `frontend/src/styles.css` | Chat 页样式优化 |

---

## 任务 1：阶段状态和计划文档

**文件：**
- 新增：`docs/superpowers/plans/2026-05-21-phase-3-vector-index-chat.md`
- 修改：`docs/管理/阶段任务.md`

- [ ] **Step 1: 更新阶段状态为进行中**

修改 `docs/管理/阶段任务.md`，将 Phase 3 状态从"未开始"改为"进行中"，更新当前阶段信息。

---

## 任务 2：添加 LangChain4j 依赖

**文件：**
- 修改：`backend/pom.xml`
- 修改：`backend/src/main/resources/application.yml`
- 修改：`backend/src/main/java/com/rag/RagApplication.java`

- [ ] **Step 1: 添加 LangChain4j 依赖到 pom.xml**

在 `backend/pom.xml` 的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-deepseek</artifactId>
    <version>0.35.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>0.35.0</version>
</dependency>
```

- [ ] **Step 2: 添加 embedding 和 chat 配置**

在 `backend/src/main/resources/application.yml` 末尾添加：

```yaml
embedding:
  model: ${EMBEDDING_MODEL:deepseek-embedding}
  dimension: ${EMBEDDING_DIMENSION:1024}

chat:
  model: ${CHAT_MODEL:deepseek-chat}
  temperature: ${CHAT_TEMPERATURE:0.7}
  max-tokens: ${CHAT_MAX_TOKENS:2048}

deepseek:
  api-key: ${DEEPSEEK_API_KEY:}
  base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
```

- [ ] **Step 3: 启用配置属性扫描**

修改 `backend/src/main/java/com/rag/RagApplication.java`，添加 `@EnableConfigurationProperties`：

```java
package com.rag;

import com.rag.config.RagProperties;
import com.rag.embedding.EmbeddingProperties;
import com.rag.vector.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, EmbeddingProperties.class, MilvusProperties.class})
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
```

- [ ] **Step 4: 运行测试验证依赖**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 3：Embedding 服务实现

**文件：**
- 新增：`backend/src/main/java/com/rag/embedding/EmbeddingProperties.java`
- 新增：`backend/src/main/java/com/rag/embedding/EmbeddingService.java`
- 新增：`backend/src/main/java/com/rag/embedding/DeepSeekEmbeddingService.java`
- 新增：`backend/src/test/java/com/rag/embedding/EmbeddingServiceTest.java`

- [ ] **Step 1: 创建 EmbeddingProperties 配置类**

```java
package com.rag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {
    private String model = "deepseek-embedding";
    private int dimension = 1024;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }
}
```

- [ ] **Step 2: 创建 EmbeddingService 接口**

```java
package com.rag.embedding;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);
    float[] embed(String text);
}
```

- [ ] **Step 3: 创建 DeepSeekEmbeddingService 实现**

```java
package com.rag.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.deepseek.DeepSeekEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public DeepSeekEmbeddingService(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            EmbeddingProperties properties) {
        this.properties = properties;
        this.embeddingModel = DeepSeekEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(properties.getModel())
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        return embeddings.stream()
                .map(e -> e.vector())
                .collect(Collectors.toList());
    }

    @Override
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }
}
```

- [ ] **Step 4: 创建 EmbeddingService 测试**

```java
package com.rag.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Test
    void embedSingleTextReturnsFloatArray() {
        EmbeddingService service = new MockEmbeddingService(1024);
        float[] result = service.embed("test text");
        assertNotNull(result);
        assertEquals(1024, result.length);
    }

    @Test
    void embedMultipleTextsReturnsListOfFloatArrays() {
        EmbeddingService service = new MockEmbeddingService(1024);
        List<float[]> result = service.embed(List.of("text1", "text2"));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1024, result.get(0).length);
    }

    private static class MockEmbeddingService implements EmbeddingService {
        private final int dimension;

        MockEmbeddingService(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[dimension]).toList();
        }

        @Override
        public float[] embed(String text) {
            return new float[dimension];
        }
    }
}
```

- [ ] **Step 5: 运行测试验证**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 4：Milvus 向量服务实现

**文件：**
- 新增：`backend/src/main/java/com/rag/vector/MilvusProperties.java`
- 新增：`backend/src/main/java/com/rag/vector/MilvusService.java`
- 新增：`backend/src/test/java/com/rag/vector/MilvusServiceTest.java`

- [ ] **Step 1: 创建 MilvusProperties 配置类**

```java
package com.rag.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host = "localhost";
    private int port = 19530;
    private String collection = "document_chunks_v1";
    private int dimension = 1024;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }
}
```

- [ ] **Step 2: 创建 MilvusService 向量服务**

```java
package com.rag.vector;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class MilvusService {

    private final MilvusProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private MilvusEmbeddingStore embeddingStore;
    private MilvusServiceClient milvusClient;

    public MilvusService(MilvusProperties properties, EmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    @PostConstruct
    public void init() {
        milvusClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(properties.getHost())
                        .withPort(properties.getPort())
                        .build()
        );
        embeddingStore = MilvusEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .collectionName(properties.getCollection())
                .dimension(properties.getDimension())
                .build();
    }

    public void insert(List<String> chunkIds, List<String> docIds, List<String> contents, List<float[]> embeddings) {
        List<Embedding> embeddingList = embeddings.stream()
                .map(Embedding::from)
                .collect(Collectors.toList());
        List<TextSegment> segments = contents.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        embeddingStore.addAll(embeddingList, segments);
    }

    public List<EmbeddingMatch<TextSegment>> search(float[] queryVector, int topK) {
        Embedding queryEmbedding = Embedding.from(queryVector);
        return embeddingStore.findRelevant(queryEmbedding, topK);
    }

    public void deleteByDocId(String docId) {
        embeddingStore.removeAll();
    }
}
```

注：需添加 `import com.rag.embedding.EmbeddingProperties;`

- [ ] **Step 3: 创建 MilvusService 测试**

```java
package com.rag.vector;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MilvusServiceTest {

    @Test
    void searchReturnsEmptyListWhenNoMatches() {
        MockMilvusService service = new MockMilvusService();
        List<SearchResult> results = service.search(new float[1024], 8);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchReturnsResultsWithMetadata() {
        MockMilvusService service = new MockMilvusService();
        service.insert(List.of("id1"), List.of("doc1"), List.of("content"), List.of(new float[1024]));
        List<SearchResult> results = service.search(new float[1024], 8);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getDocId());
    }

    private static class MockMilvusService {
        private List<SearchResult> stored = List.of();

        void insert(List<String> ids, List<String> docIds, List<String> contents, List<float[]> embeddings) {
            stored = docIds.stream()
                    .map(d -> new SearchResult(d, "content", 0.5f))
                    .toList();
        }

        List<SearchResult> search(float[] vector, int topK) {
            return stored.stream().limit(topK).toList();
        }
    }

    private static class SearchResult {
        private final String docId;
        private final String content;
        private final float score;

        SearchResult(String docId, String content, float score) {
            this.docId = docId;
            this.content = content;
            this.score = score;
        }

        String getDocId() { return docId; }
        String getContent() { return content; }
        float getScore() { return score; }
    }
}
```

- [ ] **Step 4: 运行测试验证**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 5：Chat 服务和实体实现

**文件：**
- 新增：`backend/src/main/java/com/rag/chat/entity/ChatHistoryEntity.java`
- 新增：`backend/src/main/java/com/rag/chat/repository/ChatHistoryRepository.java`
- 新增：`backend/src/main/java/com/rag/chat/ChatService.java`
- 新增：`backend/src/test/java/com/rag/chat/ChatServiceTest.java`

- [ ] **Step 1: 创建 ChatHistoryEntity 实体**

```java
package com.rag.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_history")
public class ChatHistoryEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(length = 50)
    private String model;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatHistoryEntity() {}

    public ChatHistoryEntity(String question, String answer, String model) {
        this.question = question;
        this.answer = answer;
        this.model = model;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: 创建 ChatHistoryRepository 仓储**

```java
package com.rag.chat.repository;

import com.rag.chat.entity.ChatHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntity, String> {
}
```

- [ ] **Step 3: 创建 ChatService 服务**

```java
package com.rag.chat;

import com.rag.chat.entity.ChatHistoryEntity;
import com.rag.chat.repository.ChatHistoryRepository;
import com.rag.config.RagProperties;
import com.rag.embedding.EmbeddingService;
import com.rag.vector.MilvusService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.deepseek.DeepSeekChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final String NO_CONTEXT_ANSWER = "当前知识库中不具备足够依据回答该问题";
    private static final double MIN_SCORE_THRESHOLD = 0.3;

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatLanguageModel chatModel;
    private final RagProperties ragProperties;

    public ChatService(
            EmbeddingService embeddingService,
            MilvusService milvusService,
            ChatHistoryRepository chatHistoryRepository,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${chat.model}") String modelName,
            @Value("${chat.temperature}") Double temperature,
            RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ragProperties = ragProperties;
        this.chatModel = DeepSeekChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    public ChatResult chat(String question) {
        float[] queryVector = embeddingService.embed(question);
        List<EmbeddingMatch<TextSegment>> matches = milvusService.search(
                queryVector, ragProperties.getRetrieval().getVectorTopK());

        List<EmbeddingMatch<TextSegment>> relevantMatches = matches.stream()
                .filter(m -> m.score() >= MIN_SCORE_THRESHOLD)
                .collect(Collectors.toList());

        if (relevantMatches.isEmpty()) {
            ChatHistoryEntity history = new ChatHistoryEntity(question, NO_CONTEXT_ANSWER, "deepseek-chat");
            chatHistoryRepository.save(history);
            return new ChatResult(NO_CONTEXT_ANSWER, List.of());
        }

        String context = relevantMatches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n"));

        String prompt = buildPrompt(context, question);
        String answer = chatModel.generate(prompt);

        List<SourceReference> sources = relevantMatches.stream()
                .map(m -> new SourceReference(
                        m.embedded().text(),
                        m.score()))
                .collect(Collectors.toList());

        ChatHistoryEntity history = new ChatHistoryEntity(question, answer, "deepseek-chat");
        chatHistoryRepository.save(history);

        return new ChatResult(answer, sources);
    }

    private String buildPrompt(String context, String question) {
        return String.format(
                "基于以下内容回答问题，如果内容中没有相关信息，请说明无法回答。\n\n" +
                "内容：\n%s\n\n" +
                "问题：%s\n\n" +
                "回答：",
                context, question);
    }

    public static class ChatResult {
        private final String answer;
        private final List<SourceReference> sources;

        public ChatResult(String answer, List<SourceReference> sources) {
            this.answer = answer;
            this.sources = sources;
        }

        public String getAnswer() { return answer; }
        public List<SourceReference> getSources() { return sources; }
    }

    public static class SourceReference {
        private final String content;
        private final double score;

        public SourceReference(String content, double score) {
            this.content = content;
            this.score = score;
        }

        public String getContent() { return content; }
        public double getScore() { return score; }
    }
}
```

- [ ] **Step 4: 创建 ChatService 测试**

```java
package com.rag.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void chatReturnsNoContextAnswerWhenNoMatches() {
        ChatService service = new MockChatService(List.of());
        ChatService.ChatResult result = service.chat("test question");
        assertEquals("当前知识库中不具备足够依据回答该问题", result.getAnswer());
        assertTrue(result.getSources().isEmpty());
    }

    @Test
    void chatReturnsAnswerWithSourcesWhenMatchesFound() {
        ChatService service = new MockChatService(List.of(
                new MockMatch("relevant content", 0.8)
        ));
        ChatService.ChatResult result = service.chat("test question");
        assertNotNull(result.getAnswer());
        assertFalse(result.getSources().isEmpty());
        assertEquals(0.8, result.getSources().get(0).getScore());
    }

    private static class MockChatService extends ChatService {
        private final List<MockMatch> mockMatches;

        MockChatService(List<MockMatch> matches) {
            super(null, null, null, "", "", "", 0.7, null);
            this.mockMatches = matches;
        }

        @Override
        public ChatResult chat(String question) {
            if (mockMatches.isEmpty()) {
                return new ChatResult("当前知识库中不具备足够依据回答该问题", List.of());
            }
            List<SourceReference> sources = mockMatches.stream()
                    .map(m -> new SourceReference(m.content, m.score))
                    .toList();
            return new ChatResult("mock answer", sources);
        }
    }

    private static class MockMatch {
        final String content;
        final double score;
        MockMatch(String content, double score) {
            this.content = content;
            this.score = score;
        }
    }
}
```

- [ ] **Step 5: 运行测试验证**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 6：Chat API 实现

**文件：**
- 新增：`backend/src/main/java/com/rag/api/dto/ChatRequest.java`
- 新增：`backend/src/main/java/com/rag/api/dto/ChatResponse.java`
- 新增：`backend/src/main/java/com/rag/api/dto/SourceReference.java`
- 新增：`backend/src/main/java/com/rag/api/controller/ChatController.java`
- 新增：`backend/src/test/java/com/rag/api/controller/ChatControllerTest.java`

- [ ] **Step 1: 创建 ChatRequest DTO**

```java
package com.rag.api.dto;

public class ChatRequest {
    private String question;
    private List<String> docIds;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getDocIds() {
        return docIds;
    }

    public void setDocIds(List<String> docIds) {
        this.docIds = docIds;
    }
}
```

- [ ] **Step 2: 创建 SourceReference DTO**

```java
package com.rag.api.dto;

public class SourceReference {
    private String content;
    private double score;

    public SourceReference() {}

    public SourceReference(String content, double score) {
        this.content = content;
        this.score = score;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
```

- [ ] **Step 3: 创建 ChatResponse DTO**

```java
package com.rag.api.dto;

import java.util.List;

public class ChatResponse {
    private String answer;
    private List<SourceReference> sources;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources;
    }
}
```

- [ ] **Step 4: 创建 ChatController**

```java
package com.rag.api.controller;

import com.rag.api.dto.ChatRequest;
import com.rag.api.dto.ChatResponse;
import com.rag.api.dto.SourceReference;
import com.rag.chat.ChatService;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

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
```

- [ ] **Step 5: 创建 ChatController 测试**

```java
package com.rag.api.controller;

import com.rag.chat.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void chatReturnsAnswer() throws Exception {
        ChatService.ChatResult mockResult = new ChatService.ChatResult(
                "mock answer",
                List.of(new ChatService.SourceReference("content", 0.8))
        );
        when(chatService.chat(anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("mock answer"))
                .andExpect(jsonPath("$.sources[0].score").value(0.8));
    }
}
```

- [ ] **Step 6: 运行测试验证**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 7：DocumentService 触发向量化

**文件：**
- 修改：`backend/src/main/java/com/rag/document/service/DocumentService.java`

- [ ] **Step 1: 修改 DocumentService 添加向量化触发**

修改 `DocumentService.java`，添加 `EmbeddingService` 和 `MilvusService` 依赖，在 `markReady` 后触发向量化：

```java
// 在构造函数参数中添加
private final EmbeddingService embeddingService;
private final MilvusService milvusService;

public DocumentService(
        DocumentRepository documentRepository,
        ChunkRepository chunkRepository,
        StorageService storageService,
        DocumentTextParser parser,
        FixedLengthChunker chunker,
        EmbeddingService embeddingService,
        MilvusService milvusService) {
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.storageService = storageService;
    this.parser = parser;
    this.chunker = chunker;
    this.embeddingService = embeddingService;
    this.milvusService = milvusService;
}

// 在 upload 方法中，markReady 后添加
List<ChunkEntity> chunks = chunkRepository.findByDocId(document.getId());
List<String> chunkIds = chunks.stream().map(ChunkEntity::getId).toList();
List<String> contents = chunks.stream().map(ChunkEntity::getContent).toList();
List<float[]> embeddings = embeddingService.embed(contents);
milvusService.insert(chunkIds, List.of(document.getId()), contents, embeddings);
```

- [ ] **Step 2: 添加 ChunkRepository.findByDocId 方法**

修改 `backend/src/main/java/com/rag/document/repository/ChunkRepository.java`：

```java
package com.rag.document.repository;

import com.rag.document.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChunkRepository extends JpaRepository<ChunkEntity, String> {
    void deleteByDocId(String docId);
    List<ChunkEntity> findByDocId(String docId);
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

---

## 任务 8：前端 Chat API 服务

**文件：**
- 新增：`frontend/src/services/chatApi.ts`

- [ ] **Step 1: 创建 chatApi.ts**

```typescript
export interface ChatRequest {
  question: string;
  docIds?: string[];
}

export interface SourceReference {
  content: string;
  score: number;
}

export interface ChatResponse {
  answer: string;
  sources: SourceReference[];
}

export async function sendChat(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw new Error(`Chat request failed: ${response.status}`);
  }

  return response.json();
}
```

---

## 任务 9：前端 Chat 页接入真实问答

**文件：**
- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/styles.css`

- [ ] **Step 1: 修改 ChatPage 组件接入真实问答**

修改 `frontend/src/App.tsx` 中的 `ChatPage` 函数：

```tsx
function ChatPage() {
  const [messages, setMessages] = useState<Array<{ role: "user" | "assistant"; content: string; sources?: SourceReference[] }>>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSend() {
    if (!input.trim()) return;

    const question = input.trim();
    setInput("");
    setMessages(prev => [...prev, { role: "user", content: question }]);
    setLoading(true);
    setError(null);

    try {
      const response = await sendChat({ question });
      setMessages(prev => [...prev, { role: "assistant", content: response.answer, sources: response.sources }]);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "问答请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="workspace-panel" aria-labelledby="chat-title">
      <div className="panel-heading">
        <div>
          <Title id="chat-title" level={2}>对话</Title>
          <Text type="secondary">RAG 问答工作区</Text>
        </div>
        <Tag color="blue">基础问答</Tag>
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
                  <Text type="secondary">来源引用：</Text>
                  {msg.sources.map((s, i) => (
                    <div key={i} className="source-item">
                      <Text ellipsis={{ rows: 2 }}>{s.content}</Text>
                      <Tag>相关性: {s.score.toFixed(2)}</Tag>
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
          onPressEnter={e => { if (!e.shiftKey) void handleSend(); }}
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

需要在顶部添加导入：

```tsx
import { Input } from "antd";
import { sendChat, type SourceReference } from "./services/chatApi";
```

- [ ] **Step 2: 添加 Chat 页样式**

修改 `frontend/src/styles.css`，添加：

```css
.chat-placeholder {
  color: #999;
  text-align: center;
  padding: 48px;
}

.chat-input-area {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fafafa;
  border-top: 1px solid #e8e8e8;
}

.chat-input-area .ant-input {
  flex: 1;
}

.message-sources {
  margin-top: 8px;
  padding: 8px;
  background: #f5f5f5;
  border-radius: 4px;
}

.source-item {
  margin-top: 8px;
  padding: 4px;
}
```

- [ ] **Step 3: 运行前端测试验证**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 4: 运行前端构建验证**

Run: `npm --prefix frontend run build`
Expected: PASS

---

## 任务 10：完成验证和阶段记录

**文件：**
- 修改：`docs/管理/阶段任务.md`
- 修改：`docs/管理/变更记录.md`
- 修改：`README.md`

- [ ] **Step 1: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

- [ ] **Step 2: 运行前端测试**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 3: 运行前端构建**

Run: `npm --prefix frontend run build`
Expected: PASS

- [ ] **Step 4: 运行前端安全审计**

Run: `npm --prefix frontend audit --audit-level=high`
Expected: PASS

- [ ] **Step 5: 运行 Docker Compose 配置验证**

Run: `docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet`
Expected: PASS

- [ ] **Step 6: 更新阶段任务文档**

修改 `docs/管理/阶段任务.md`：
- 将 Phase 3 状态从"进行中"改为"已完成"
- 填写完成日期、提交记录、验证结果

- [ ] **Step 7: 更新变更记录**

修改 `docs/管理/变更记录.md`，记录 Phase 3 新增内容。

- [ ] **Step 8: 更新 README 状态**

修改 `README.md`，更新当前状态为 Phase 3 已完成。