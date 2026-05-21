package com.rag.document.repository;

import com.rag.document.entity.ChunkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<ChunkEntity, String> {

    List<ChunkEntity> findByDocIdOrderByChunkIndexAsc(String docId);

    void deleteByDocId(String docId);
}
