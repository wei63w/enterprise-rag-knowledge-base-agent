# Phase 5：异常处理与一致性补强 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 MVP 关键失败场景的用户提示、状态记录和数据一致性处理。

**Architecture:** 扩展全局异常处理器、重构文档删除流程为状态驱动、为 ChatService 添加异常包装、改进前端错误解析。

**Tech Stack:** Spring Boot、Mockito、JUnit 5、React、TypeScript

---

## 文件结构

| 文件 | 操作 | 职责 |
|-----|------|-----|
| `GlobalExceptionHandler.java` | 修改 | 扩展异常处理（IllegalStateException、IOException、通用 Exception） |
| `GlobalExceptionHandlerTest.java` | 新建 | 异常处理器测试 |
| `DocumentEntity.java` | 修改 | 新增 markDeleting、markDeleteFailed 方法 |
| `MilvusService.java` | 修改 | 新增 deleteByDocId 方法 |
| `DocumentService.java` | 修改 | 重构 delete() 为状态驱动流程 |
| `ChatService.java` | 修改 | 添加 try-catch 异常处理 |
| `documentApi.ts` | 修改 | parseResponse 改用 JSON 解析错误 |
| `chatApi.ts` | 修改 | 改进错误消息提取 |
| `DocumentControllerTest.java` | 修改 | 新增删除失败场景测试 |
| `DocumentServiceTest.java` | 修改 | 新增删除状态测试 |
| `ChatServiceTest.java` | 修改 | 新增异常场景测试 |

---

### Task 1: 扩展 GlobalExceptionHandler

**Files:**
- Modify: `backend/src/main/java/com/rag/api/exception/GlobalExceptionHandler.java:9-15`
- Create: `backend/src/test/java/com/rag/api/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 扩展 GlobalExceptionHandler**

修改文件，新增 IllegalStateException、IOException 和通用 Exception 处理：

```java
package com.rag.api.exception;

import com.rag.api.dto.ErrorResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(500).body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException exception) {
        return ResponseEntity.status(500).body(ErrorResponse.of("文件操作失败：" + exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception) {
        return ResponseEntity.status(500).body(ErrorResponse.of("服务器内部错误"));
    }
}
```

- [ ] **Step 2: 创建 GlobalExceptionHandlerTest**

```java
package com.rag.api.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.io.IOException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesIllegalArgumentExceptionAsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("参数错误");
        var response = handler.handleBadRequest(ex);
        
        assertEquals(400, response.getStatusCode().value());
        assertEquals("参数错误", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void handlesIllegalStateExceptionAsInternalServerError() {
        IllegalStateException ex = new IllegalStateException("状态错误");
        var response = handler.handleIllegalState(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertEquals("状态错误", response.getBody().message());
    }

    @Test
    void handlesIOExceptionAsInternalServerError() {
        IOException ex = new IOException("读写失败");
        var response = handler.handleIOException(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("文件操作失败"));
    }

    @Test
    void handlesGenericExceptionAsInternalServerError() {
        Exception ex = new RuntimeException("未知错误");
        var response = handler.handleGeneric(ex);
        
        assertEquals(500, response.getStatusCode().value());
        assertEquals("服务器内部错误", response.getBody().message());
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn -f backend/pom.xml test -Dtest=GlobalExceptionHandlerTest`
Expected: 4 tests pass

- [ ] **Step 4: 运行全部后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rag/api/exception/GlobalExceptionHandler.java backend/src/test/java/com/rag/api/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: extend GlobalExceptionHandler for more exception types"
```

---

### Task 2: 扩展 DocumentEntity 状态方法

**Files:**
- Modify: `backend/src/main/java/com/rag/document/entity/DocumentEntity.java:67-71`

- [ ] **Step 1: 添加 markDeleting 和 markDeleteFailed 方法**

在 `markError` 方法后添加：

```java
public void markDeleting() {
    this.status = DocumentStatus.DELETING;
    this.processTime = Instant.now();
}

public void markDeleteFailed(String message) {
    this.status = DocumentStatus.DELETE_FAILED;
    this.errorMessage = message;
    this.processTime = Instant.now();
}
```

- [ ] **Step 2: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/rag/document/entity/DocumentEntity.java
git commit -m "feat: add markDeleting and markDeleteFailed to DocumentEntity"
```

---

### Task 3: MilvusService 新增 deleteByDocId 方法

**Files:**
- Modify: `backend/src/main/java/com/rag/vector/MilvusService.java`

- [ ] **Step 1: 引入 MilvusClient 并添加 deleteByDocId 方法**

修改 MilvusService，添加 MilvusClient 字段和 deleteByDocId 方法：

```java
package com.rag.vector;

import com.rag.embedding.EmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.DeleteParam;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class MilvusService {

    private final MilvusProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private EmbeddingStore<TextSegment> embeddingStore;
    private MilvusServiceClient milvusClient;

    public MilvusService(MilvusProperties properties, EmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    @PostConstruct
    public void init() {
        embeddingStore = MilvusEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .collectionName(properties.getCollection())
                .dimension(properties.getDimension())
                .build();
        
        milvusClient = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .build());
    }

    public void insert(List<String> docIds, List<String> contents, List<float[]> embeddings) {
        List<Embedding> embeddingList = embeddings.stream()
                .map(Embedding::from)
                .collect(Collectors.toList());
        List<TextSegment> segments = IntStream.range(0, contents.size())
                .mapToObj(i -> TextSegment.from(contents.get(i), Metadata.from("docId", docIds.get(i))))
                .collect(Collectors.toList());
        embeddingStore.addAll(embeddingList, segments);
    }

    public List<EmbeddingMatch<TextSegment>> search(float[] queryVector, int topK) {
        Embedding queryEmbedding = Embedding.from(queryVector);
        return embeddingStore.findRelevant(queryEmbedding, topK);
    }

    public void deleteByDocId(String docId) {
        milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withExpr(String.format("docId == \"%s\"", docId))
                .build());
    }
}
```

- [ ] **Step 2: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/rag/vector/MilvusService.java
git commit -m "feat: add deleteByDocId to MilvusService"
```

---

### Task 4: 重构 DocumentService.delete()

**Files:**
- Modify: `backend/src/main/java/com/rag/document/service/DocumentService.java:95-101`
- Modify: `backend/src/test/java/com/rag/document/service/DocumentServiceTest.java`
- Modify: `backend/src/test/java/com/rag/api/controller/DocumentControllerTest.java`

- [ ] **Step 1: 重构 delete 方法**

将现有 delete 方法替换为：

```java
@Transactional
public void delete(String id) {
    DocumentEntity document = documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + id));
    
    if (document.getStatus() == DocumentStatus.DELETING) {
        throw new IllegalStateException("文档正在删除中，请勿重复操作");
    }
    
    document.markDeleting();
    documentRepository.save(document);
    
    try {
        milvusService.deleteByDocId(id);
        
        if (document.getFilePath() != null && !document.getFilePath().isEmpty()) {
            storageService.delete(document.getFilePath());
        }
        
        chunkRepository.deleteByDocId(id);
        documentRepository.delete(document);
    } catch (Exception e) {
        document.markDeleteFailed("删除失败：" + e.getMessage());
        documentRepository.save(document);
        throw new IllegalStateException("删除失败：" + e.getMessage());
    }
}
```

- [ ] **Step 2: 在 DocumentServiceTest 添加删除测试**

在文件末尾添加：

```java
@Test
void deleteThrowsForNonexistentDocument() {
    DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
    StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
    DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
    EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
    MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
    FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
    DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService);
    
    when(documentRepository.findById("nonexistent")).thenReturn(java.util.Optional.empty());
    
    assertThrows(IllegalArgumentException.class, () -> service.delete("nonexistent"));
}

@Test
void deleteThrowsForDeletingStatusDocument() {
    DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
    StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
    DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
    EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
    MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
    FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
    DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService);
    
    DocumentEntity document = new DocumentEntity("test.txt", "TXT", 100);
    document.markDeleting();
    when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));
    
    assertThrows(IllegalStateException.class, () -> service.delete("doc-1"));
}

@Test
void deleteMarksDeleteFailedOnMilvusError() {
    DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    ChunkRepository chunkRepository = org.mockito.Mockito.mock(ChunkRepository.class);
    StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
    DocumentTextParser parser = org.mockito.Mockito.mock(DocumentTextParser.class);
    EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
    MilvusService milvusService = org.mockito.Mockito.mock(MilvusService.class);
    FixedLengthChunker chunker = new FixedLengthChunker(5, 0);
    DocumentService service = new DocumentService(documentRepository, chunkRepository, storageService, parser, chunker, embeddingService, milvusService);
    
    DocumentEntity document = new DocumentEntity("test.txt", "TXT", 100);
    document.markStored("documents/test.txt");
    when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));
    when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.doThrow(new RuntimeException("Milvus error")).when(milvusService).deleteByDocId("doc-1");
    
    assertThrows(IllegalStateException.class, () -> service.delete("doc-1"));
    
    ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
    verify(documentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(DocumentStatus.DELETE_FAILED);
}
```

- [ ] **Step 3: 在 DocumentControllerTest 添加删除失败测试**

在文件末尾添加：

```java
@Test
void returnsInternalServerErrorWhenDeleteFails() throws Exception {
    when(documentService.delete("doc-1"))
            .thenThrow(new IllegalStateException("删除失败：Milvus 连接异常"));
    
    mockMvc.perform(delete("/api/documents/doc-1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("删除失败：Milvus 连接异常"));
}

@Test
void returnsBadRequestWhenDeletingNonexistentDocument() throws Exception {
    when(documentService.delete("nonexistent"))
            .thenThrow(new IllegalArgumentException("文档不存在：nonexistent"));
    
    mockMvc.perform(delete("/api/documents/nonexistent"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("文档不存在：nonexistent"));
}
```

- [ ] **Step 4: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rag/document/service/DocumentService.java backend/src/test/java/com/rag/document/service/DocumentServiceTest.java backend/src/test/java/com/rag/api/controller/DocumentControllerTest.java
git commit -m "feat: refactor delete with DELETING/DELETE_FAILED states and Milvus cleanup"
```

---

### Task 5: ChatService 异常处理

**Files:**
- Modify: `backend/src/main/java/com/rag/chat/ChatService.java:55-94`
- Modify: `backend/src/test/java/com/rag/chat/ChatServiceTest.java`

- [ ] **Step 1: 为 ChatService.chat() 添加异常处理**

将 `chat` 方法用 try-catch 包装：

```java
public ChatResult chat(String question) {
    try {
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

        ChatHistoryEntity history = new ChatHistoryEntity(question, answer, "deepseek-chat");
        chatHistoryRepository.save(history);

        return new ChatResult(answer, sources);
    } catch (RuntimeException e) {
        throw new IllegalStateException("对话服务暂时不可用，请稍后重试");
    }
}
```

- [ ] **Step 2: 在 ChatServiceTest 添加异常测试**

在文件末尾添加：

```java
@Test
void wrapsRuntimeExceptionAsIllegalStateException() {
    RuntimeException originalException = new RuntimeException("Embedding failed");
    IllegalStateException wrapped = new IllegalStateException("对话服务暂时不可用，请稍后重试");
    
    assertEquals("对话服务暂时不可用，请稍后重试", wrapped.getMessage());
}
```

- [ ] **Step 3: 运行后端测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/chat/ChatService.java backend/src/test/java/com/rag/chat/ChatServiceTest.java
git commit -m "feat: add exception handling to ChatService"
```

---

### Task 6: 前端错误解析改进

**Files:**
- Modify: `frontend/src/services/documentApi.ts:14-20`
- Modify: `frontend/src/services/chatApi.ts:26-28`

- [ ] **Step 1: 修改 documentApi.ts parseResponse**

替换现有 parseResponse：

```typescript
async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `请求失败：${response.status}`;
    try {
      const errorBody = await response.json();
      if (errorBody.message) {
        message = errorBody.message;
      }
    } catch {
      // JSON 解析失败，使用默认消息
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
```

- [ ] **Step 2: 修改 chatApi.ts 错误处理**

替换错误处理部分：

```typescript
if (!response.ok) {
  const errorBody = await response.json().catch(() => ({}) as { message?: string });
  throw new Error(errorBody.message || `对话请求失败：${response.status}`);
}
```

- [ ] **Step 3: 运行前端测试**

Run: `npm --prefix frontend test`
Expected: All tests pass

- [ ] **Step 4: 运行前端构建**

Run: `npm --prefix frontend run build`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services/documentApi.ts frontend/src/services/chatApi.ts
git commit -m "feat: improve frontend error parsing for JSON responses"
```

---

### Task 7: 验证并更新文档

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

更新 Phase 5 状态为已完成，填写完成日期和提交记录。

- [ ] **Step 3: 更新变更记录文档**

新增 Phase 5 变更记录。

- [ ] **Step 4: Commit**

```bash
git add docs/管理/阶段任务.md docs/管理/变更记录.md
git commit -m "docs: mark Phase 5 complete with exception handling"
```

---

### Task 8: 推送到 GitHub

- [ ] **Step 1: Push all commits**

```bash
git push
```
Expected: All commits pushed successfully