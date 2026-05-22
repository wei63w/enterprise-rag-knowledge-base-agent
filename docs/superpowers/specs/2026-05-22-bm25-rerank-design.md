# Phase 6b：BM25 与 Rerank 检索增强 - 设计文档

日期：2026-05-22
阶段：Phase 6b
状态：待实施

## 1. BM25 关键词索引

### 新增 BM25KeywordService

使用 Apache Lucene 内存索引，对切片内容建 BM25 索引：

```java
@Service
public class BM25KeywordService {
    private Directory indexDir;
    private StandardAnalyzer analyzer;

    @PostConstruct
    public void init() {
        this.indexDir = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
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
                        Document doc = searcher.storedFields().document(hit.doc);
                        return new KeywordHit(
                            doc.get("docId"),
                            doc.get("content"),
                            hit.score
                        );
                    })
                    .toList();
        }
    }

    public void deleteByDocId(String docId) {
        try (IndexWriter writer = new IndexWriter(indexDir, new IndexWriterConfig(analyzer))) {
            writer.deleteDocuments(new Term("docId", docId));
        }
    }
}
```

### 依赖

Lucene 已通过 langchain4j 间接引入，无需额外依赖。

### DocumentService 集成

文档上传后同时调用 `bm25Service.indexChunks(docId, contents)`，删除时调用 `bm25Service.deleteByDocId(docId)`。

## 2. RRF 融合排序

### RRF 算法

Reciprocal Rank Fusion，k=60：

```
score(d) = Σ 1/(60 + rank_i(d))
```

- rank_i 是文档在第 i 个检索结果列表中的排名（从 1 开始）

### 检索流程改造

ChatService.chat() 中的检索步骤：

```
1. 向量检索 → topK × 2（如 16）条
2. BM25 检索 → topK × 2 条
3. RRF 融合去重 → 取 topK（如 8）条
4. 筛选 RRF 分 > 阈值 → 构建 prompt
```

### RRF 融合代码

```java
private List<ScoredChunk> rrfMerge(List<VectorHit> vectorHits, List<KeywordHit> bm25Hits, int topK) {
    Map<String, ScoredChunk> dedup = new LinkedHashMap<>();
    double k = 60.0;
    
    for (int i = 0; i < vectorHits.size(); i++) {
        String key = vectorHits.get(i).docId() + ":" + vectorHits.get(i).chunkIndex();
        double rrf = 1.0 / (k + i + 1);
        dedup.merge(key, new ScoredChunk(vectorHits.get(i)), (a, b) -> { a.addRrf(rrf); return a; });
    }
    for (int i = 0; i < bm25Hits.size(); i++) {
        String key = bm25Hits.get(i).docId() + ":" + bm25Hits.get(i).chunkIndex();
        double rrf = 1.0 / (k + i + 1);
        dedup.merge(key, new ScoredChunk(bm25Hits.get(i)), (a, b) -> { a.addRrf(rrf); return a; });
    }
    
    return dedup.values().stream()
            .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
            .limit(topK)
            .toList();
}
```

## 3. 配置变化

### application.yml 新增

```yaml
rag:
  retrieval:
    vector-top-k: 8
    bm25-top-k: 8
    rrf-top-k: 8
```

### RagProperties 新增字段

```java
public static class Retrieval {
    private int vectorTopK = 8;
    private int bm25TopK = 8;
    private int rrfTopK = 8;
    // getters/setters
}
```

## 4. 文件结构

| 文件 | 操作 | 职责 |
|-----|------|-----|
| `BM25KeywordService.java` | 新建 | Lucene BM25 索引与检索 |
| `BM25KeywordServiceTest.java` | 新建 | BM25 索引/检索/删除测试 |
| `ChatService.java` | 修改 | 集成 BM25 + RRF 融合流程 |
| `DocumentService.java` | 修改 | 上传/删除时调用 BM25 |
| `RagProperties.java` | 修改 | 新增 bm25TopK、rrfTopK |
| `application.yml` | 修改 | 新增配置项 |

## 5. 测试

| 测试类 | 测试场景 |
|-------|---------|
| BM25KeywordServiceTest | indexChunks 后可 search 到内容 |
| BM25KeywordServiceTest | deleteByDocId 后 search 不到 |
| BM25KeywordServiceTest | 空查询返回空结果 |
| ChatServiceTest | RRF 融合排序正确 |

## 6. 完成标准

- 专业术语和编号类查询召回更稳定（关键词匹配补强）。
- 检索增强逻辑有可验证测试。
- 不影响现有向量检索功能。