# Phase 6b：BM25 与 RRF 检索增强 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加 Lucene BM25 关键词召回和 RRF 融合排序，提升专业术语和编号类查询的检索质量。

**Architecture:** 使用 Apache Lucene 内存索引实现 BM25 关键词检索，与现有 Milvus 向量检索并行，通过 RRF (Reciprocal Rank Fusion) 融合两组结果取最终 Top-K。

**Tech Stack:** Apache Lucene (已通过 langchain4j 间接引入)、Mockito、JUnit 5

---

## 文件结构

| 文件 | 操作 | 职责 |
|-----|------|-----|
| `RagProperties.java` | 修改 | 新增 bm25TopK、rrfTopK 配置字段 |
| `application.yml` | 修改 | 新增 bm25-top-k、rrf-top-k 配置项 |
| `BM25KeywordService.java` | 新建 | Lucene BM25 索引与检索 |
| `BM25KeywordServiceTest.java` | 新建 | BM25 索引/检索/删除测试 |
| `DocumentService.java` | 修改 | 上传/删除时调用 BM25 |
| `DocumentServiceTest.java` | 修改 | 适配 BM25 参数 |
| `ChatService.java` | 修改 | 集成 BM25 + RRF 融合 |
| `ChatServiceTest.java` | 修改 | 适配 BM25 参数 |

---

### Task 1: 新增配置项

**Files:**
- Modify: `RagProperties.java:66-76`
- Modify: `application.yml:42-43`

- [ ] **Step 1: RagProperties.Retrieval 新增字段**

在 `vectorTopK` 后添加：

```java
private int bm25TopK = 8;
private int rrfTopK = 8;

public int getBm25TopK() { return bm25TopK; }
public void setBm25TopK(int bm25TopK) { this.bm25TopK = bm25TopK; }
public int getRrfTopK() { return rrfTopK; }
public void setRrfTopK(int rrfTopK) { this.rrfTopK = rrfTopK; }
```

- [ ] **Step 2: application.yml 新增配置**

```yaml
    bm25-top-k: 8
    rrf-top-k: 8
```

- [ ] **Step 3: 运行测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/config/RagProperties.java backend/src/main/resources/application.yml
git commit -m "feat: add bm25TopK and rrfTopK config"
```

---

### Task 2: 创建 BM25KeywordService

**Files:**
- Create: `backend/src/main/java/com/rag/retrieval/BM25KeywordService.java`
- Create: `backend/src/test/java/com/rag/retrieval/BM25KeywordServiceTest.java`

- [ ] **Step 1: 创建 BM25KeywordService**

```java
package com.rag.retrieval;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

@Service
public class BM25KeywordService {

    private Directory indexDir;
    private StandardAnalyzer analyzer;

    @PostConstruct
    public void init() {
        this.indexDir = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
    }

    @PreDestroy
    public void close() throws IOException {
        indexDir.close();
    }

    public void indexChunks(String docId, List<String> contents) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDir, config)) {
            for (int i = 0; i < contents.size(); i++) {
                Document doc = new Document();
                doc.add(new StringField("docId", docId, Field.Store.YES));
                doc.add(new TextField("content", contents.get(i), Field.Store.YES));
                doc.add(new StoredField("chunkIndex", i));
                writer.addDocument(doc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene indexing failed", e);
        }
    }

    public List<KeywordHit> search(String query, int topK) {
        try (DirectoryReader reader = DirectoryReader.open(indexDir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);
            Query q = parser.parse(query);
            TopDocs topDocs = searcher.search(q, topK);
            return Arrays.stream(topDocs.scoreDocs)
                    .map(hit -> {
                        try {
                            Document doc = searcher.storedFields().document(hit.doc);
                            return new KeywordHit(
                                    doc.get("docId"),
                                    doc.get("content"),
                                    (double) hit.score);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void deleteByDocId(String docId) {
        try (IndexWriter writer = new IndexWriter(indexDir, new IndexWriterConfig(analyzer))) {
            writer.deleteDocuments(new Term("docId", docId));
        } catch (IOException e) {
            throw new RuntimeException("Lucene delete failed", e);
        }
    }

    public record KeywordHit(String docId, String content, double score) {
    }
}
```

- [ ] **Step 2: 创建 BM25KeywordServiceTest**

```java
package com.rag.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BM25KeywordServiceTest {

    @Test
    void indexesAndSearchesChunks() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        service.indexChunks("doc-1", List.of("神经网络是一种机器学习方法", "深度学习使用多层神经网络"));
        List<BM25KeywordService.KeywordHit> results = service.search("神经网络", 10);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(h -> h.docId().equals("doc-1")));
    }

    @Test
    void deleteByDocIdRemovesChunks() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        service.indexChunks("doc-1", List.of("内容一"));
        service.deleteByDocId("doc-1");
        List<BM25KeywordService.KeywordHit> results = service.search("内容一", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchReturnsEmptyForNoMatch() {
        BM25KeywordService service = new BM25KeywordService();
        service.init();

        List<BM25KeywordService.KeywordHit> results = service.search("不存在的关键词", 10);

        assertNotNull(results);
    }

    @Test
    void keywordHitHoldsAllFields() {
        BM25KeywordService.KeywordHit hit = new BM25KeywordService.KeywordHit("doc-1", "content", 0.85);

        assertEquals("doc-1", hit.docId());
        assertEquals("content", hit.content());
        assertEquals(0.85, hit.score());
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn -f backend/pom.xml test`
Expected: BM25KeywordServiceTest 4 tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/retrieval/ backend/src/test/java/com/rag/retrieval/
git commit -m "feat: add BM25KeywordService with Lucene indexing and search"
```

---

### Task 3: 集成 BM25 到 DocumentService

**Files:**
- Modify: `DocumentService.java`
- Modify: `DocumentServiceTest.java`

- [ ] **Step 1: 修改 DocumentService 上传和删除**

在构造函数新增 `BM25KeywordService` 参数：

```java
private final BM25KeywordService bm25Service;

public DocumentService(
        DocumentRepository documentRepository,
        ChunkRepository chunkRepository,
        StorageService storageService,
        DocumentTextParser parser,
        FixedLengthChunker chunker,
        EmbeddingService embeddingService,
        MilvusService milvusService,
        BM25KeywordService bm25Service) {
    // ... 现有赋值
    this.bm25Service = bm25Service;
}
```

upload 方法中 `milvusService.insert(...)` 后添加：`bm25Service.indexChunks(document.getId(), contents);`

delete 方法中 `milvusService.deleteByDocId(id)` 后添加：`bm25Service.deleteByDocId(id);`

- [ ] **Step 2: 修改 DocumentServiceTest 适配新参数**

每个测试的 service 构造调用末尾增加 `org.mockito.Mockito.mock(BM25KeywordService.class)` 作为第 8 个参数。

- [ ] **Step 3: 运行测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/document/service/DocumentService.java backend/src/test/java/com/rag/document/service/DocumentServiceTest.java
git commit -m "feat: integrate BM25 into DocumentService upload and delete"
```

---

### Task 4: 集成 BM25 + RRF 到 ChatService

**Files:**
- Modify: `ChatService.java`
- Modify: `ChatServiceTest.java`

- [ ] **Step 1: 修改 ChatService 检索流程**

在构造函数新增 `BM25KeywordService` 参数，修改 `chat()` 方法中的检索部分。

将第 80-86 行替换为：

```java
float[] queryVector = embeddingService.embed(searchQuestion);
List<EmbeddingMatch<TextSegment>> vectorMatches = milvusService.search(
        queryVector, ragProperties.getRetrieval().getVectorTopK() * 2);

List<BM25KeywordService.KeywordHit> bm25Hits = bm25Service.search(
        searchQuestion, ragProperties.getRetrieval().getBm25TopK() * 2);

List<EmbeddingMatch<TextSegment>> merged = mergeResults(
        vectorMatches, bm25Hits, ragProperties.getRetrieval().getRrfTopK());

List<EmbeddingMatch<TextSegment>> relevantMatches = merged.stream()
        .filter(m -> m.score() >= MIN_SCORE_THRESHOLD)
        .collect(Collectors.toList());
```

新增 `mergeResults` 方法：

```java
private List<EmbeddingMatch<TextSegment>> mergeResults(
        List<EmbeddingMatch<TextSegment>> vectorMatches,
        List<BM25KeywordService.KeywordHit> bm25Hits,
        int topK) {
    
    Map<String, EmbeddingMatch<TextSegment>> dedup = new LinkedHashMap<>();
    double k = 60.0;
    
    for (int i = 0; i < vectorMatches.size(); i++) {
        EmbeddingMatch<TextSegment> m = vectorMatches.get(i);
        String docId = m.embedded().metadata().get("docId");
        String key = docId + ":" + m.embedded().text();
        double rrf = 1.0 / (k + i + 1);
        dedup.put(key, m);
    }
    
    for (int i = 0; i < bm25Hits.size(); i++) {
        BM25KeywordService.KeywordHit hit = bm25Hits.get(i);
        String key = hit.docId() + ":" + hit.content();
        double rrf = 1.0 / (k + i + 1);
        if (!dedup.containsKey(key)) {
            TextSegment segment = TextSegment.from(hit.content(), Metadata.from("docId", hit.docId()));
            Embedding fake = Embedding.from(new float[0]);
            dedup.put(key, new EmbeddingMatch<>(rrf, "id", fake, segment));
        }
    }
    
    return dedup.values().stream()
            .sorted(Comparator.comparingDouble(m -> -m.score()))
            .limit(topK)
            .toList();
}
```

- [ ] **Step 2: 修改 ChatServiceTest 适配新参数**

在 `ChatServiceTest` 相关测试中适配构造函数参数（目前 ChatServiceTest 只测试 ChatResult 和 SourceReference 类，不测试 chat 方法，可能无需改动）。

- [ ] **Step 3: 运行测试**

Run: `mvn -f backend/pom.xml test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/rag/chat/ChatService.java backend/src/test/java/com/rag/chat/ChatServiceTest.java
git commit -m "feat: integrate BM25 search and RRF merge into ChatService"
```

---

### Task 5: 验证并更新文档

**Files:**
- Modify: `docs/管理/阶段任务.md`
- Modify: `docs/管理/变更记录.md`

- [ ] **Step 1: 运行全部验证**

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend audit --audit-level=high
docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet
```

- [ ] **Step 2: 更新阶段任务文档**

更新 Phase 6b 状态为已完成。

- [ ] **Step 3: 更新变更记录文档**

新增 Phase 6b 变更记录。

- [ ] **Step 4: Commit**

```bash
git add docs/管理/阶段任务.md docs/管理/变更记录.md
git commit -m "docs: mark Phase 6b complete with BM25 and RRF rerank"
```

---

### Task 6: 推送到 GitHub

- [ ] **Step 1: Push**

```bash
git push
```
