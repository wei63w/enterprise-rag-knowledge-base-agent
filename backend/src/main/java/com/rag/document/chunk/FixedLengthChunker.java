package com.rag.document.chunk;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FixedLengthChunker {

    private final int chunkSize;
    private final int overlap;

    public FixedLengthChunker() {
        this(500, 100);
    }

    public FixedLengthChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.strip();
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        return chunks;
    }
}
