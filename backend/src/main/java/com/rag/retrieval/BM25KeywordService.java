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
