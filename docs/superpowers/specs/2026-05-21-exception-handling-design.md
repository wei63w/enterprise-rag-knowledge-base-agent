# Phase 5：异常处理与一致性补强 - 设计文档

日期：2026-05-21
阶段：Phase 5
状态：待实施

## 1. 统一错误响应

### ErrorResponse 结构

保持现有结构不变：

```java
public record ErrorResponse(String message, Instant timestamp) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, Instant.now());
    }
}
```

### GlobalExceptionHandler 扩展

新增异常类型处理：

| 异常类型 | HTTP 状态码 | 场景 |
|---------|------------|------|
| IllegalArgumentException | 400 | 参数验证失败、资源不存在 |
| IllegalStateException | 500 | 业务状态不允许、处理失败 |
| IOException | 500 | 文件读写失败 |
| 其他 Exception | 500 | 未预期的异常（兜底） |

实现代码：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(500).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException e) {
        return ResponseEntity.status(500).body(ErrorResponse.of("文件操作失败：" + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        return ResponseEntity.status(500).body(ErrorResponse.of("服务器内部错误"));
    }
}
```

## 2. 文档删除一致性

### MilvusService 新增方法

```java
public void deleteByDocId(String docId) {
    // 删除 metadata.docId = docId 的所有向量
    milvusClient.delete(DeleteParam.builder()
        .collectionName(collectionName)
        .expr(String.format("docId == \"%s\"", docId))
        .build());
}
```

### DocumentService.delete() 流程

```
1. 查询文档，不存在则抛 IllegalArgumentException
2. 检查状态：DELETING 状态拒绝删除（抛 IllegalStateException）
3. 标记状态为 DELETING，保存到数据库
4. 执行删除步骤（顺序执行）：
   a. 删除 Milvus 向量（按 docId）
   b. 删除 MinIO 文件（按 objectKey）
   c. 删除 MySQL chunks（按 documentId）
   d. 删除 MySQL document
5. 全部成功 → 文档已删除
6. 任一步骤失败 → 标记状态为 DELETE_FAILED，记录错误信息
```

### 状态约束

| 状态 | 可删除 | 说明 |
|-----|-------|------|
| PROCESSING | 是 | 中断处理流程 |
| READY | 是 | 正常删除 |
| ERROR | 是 | 删除失败文档 |
| DELETING | 否 | 正在删除中，拒绝重复操作 |
| DELETE_FAILED | 是 | 可重试删除 |

### 实现代码

```java
public void delete(String id) {
    DocumentEntity document = documentRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + id));
    
    if (document.getStatus() == DocumentStatus.DELETING) {
        throw new IllegalStateException("文档正在删除中，请勿重复操作");
    }
    
    document.setStatus(DocumentStatus.DELETING);
    documentRepository.save(document);
    
    try {
        // 1. 删除 Milvus 向量
        milvusService.deleteByDocId(id);
        
        // 2. 删除 MinIO 文件
        if (document.getObjectKey() != null) {
            storageService.delete(document.getObjectKey());
        }
        
        // 3. 删除 MySQL chunks
        chunkRepository.deleteByDocumentId(id);
        
        // 4. 删除 MySQL document
        documentRepository.delete(document);
    } catch (Exception e) {
        document.setStatus(DocumentStatus.DELETE_FAILED);
        document.setErrorMessage("删除失败：" + e.getMessage());
        documentRepository.save(document);
        throw new IllegalStateException("删除失败：" + e.getMessage());
    }
}
```

## 3. ChatService 异常处理

### 异常处理点

| 步骤 | 异常类型 | 用户提示 |
|-----|---------|---------|
| embedding 失败 | RuntimeException | "对话服务暂时不可用，请稍后重试" |
| milvus search 失败 | RuntimeException | "对话服务暂时不可用，请稍后重试" |
| LLM 调用失败 | RuntimeException | "对话服务暂时不可用，请稍后重试" |
| 无相关结果 | 正常返回 | "未找到相关文档内容，请上传相关文档后再提问" |

### 实现代码

```java
public ChatResponse chat(String question) {
    try {
        float[] vector = embeddingService.embed(question);
        List<EmbeddingMatch<TextSegment>> matches = milvusService.search(vector, properties.getTopK());
        
        if (matches.isEmpty()) {
            return new ChatResponse("未找到相关文档内容，请上传相关文档后再提问", List.of());
        }
        
        // 构建 prompt，调用 LLM
        String answer = llmService.chat(prompt);
        return new ChatResponse(answer, sources);
    } catch (RuntimeException e) {
        log.error("Chat failed for question: {}", question, e);
        throw new IllegalStateException("对话服务暂时不可用，请稍后重试");
    }
}
```

## 4. 前端错误展示

### documentApi.ts parseResponse 改进

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
  return response.json();
}
```

### chatApi.ts 错误处理改进

```typescript
async function chat(question: string): Promise<ChatResponse> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  });
  
  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}) as ErrorBody);
    throw new Error(errorBody.message || `对话请求失败：${response.status}`);
  }
  
  return response.json();
}
```

## 5. 异常场景测试

### 后端测试用例

| 测试类 | 测试场景 | 验证点 |
|-------|---------|--------|
| GlobalExceptionHandlerTest | IllegalArgumentException | 返回 400，body 包含 message |
| GlobalExceptionHandlerTest | IllegalStateException | 返回 500，body 包含 message |
| DocumentServiceTest | 删除不存在文档 | 抛 IllegalArgumentException |
| DocumentServiceTest | 删除 DELETING 状态文档 | 抛 IllegalStateException |
| DocumentServiceTest | 删除成功 | Milvus/MinIO/chunks/document 全部删除 |
| DocumentServiceTest | 删除失败（Milvus 异常） | 状态变为 DELETE_FAILED |
| MilvusServiceTest | deleteByDocId 正常删除 | 向量被删除 |
| ChatServiceTest | embedding 失败 | 抛 IllegalStateException |
| ChatServiceTest | 无匹配结果 | 返回提示消息 |

### 验证命令

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend audit --audit-level=high
docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet
```

## 6. 完成标准

- 常见失败场景不会导致文档永久停留在 PROCESSING 状态
- 删除失败时能标记 DELETE_FAILED 并提示可重试
- 前端展示用户可理解的错误信息（中文）
- 所有异常场景测试通过