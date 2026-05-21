package com.rag.embedding;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);
    float[] embed(String text);
}