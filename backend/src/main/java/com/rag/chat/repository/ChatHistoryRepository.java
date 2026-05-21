package com.rag.chat.repository;

import com.rag.chat.entity.ChatHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntity, String> {
}