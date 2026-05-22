package com.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Memory memory = new Memory();

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public static class Chunk {
        private String defaultStrategy = "sliding-window";
        private int fixedLength = 500;
        private int overlap = 100;

        public String getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(String defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        public int getFixedLength() {
            return fixedLength;
        }

        public void setFixedLength(int fixedLength) {
            this.fixedLength = fixedLength;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    public static class Retrieval {
        private int vectorTopK = 8;
        private int bm25TopK = 8;
        private int rrfTopK = 8;

        public int getVectorTopK() {
            return vectorTopK;
        }

        public void setVectorTopK(int vectorTopK) {
            this.vectorTopK = vectorTopK;
        }

        public int getBm25TopK() {
            return bm25TopK;
        }

        public void setBm25TopK(int bm25TopK) {
            this.bm25TopK = bm25TopK;
        }

        public int getRrfTopK() {
            return rrfTopK;
        }

        public void setRrfTopK(int rrfTopK) {
            this.rrfTopK = rrfTopK;
        }
    }

    public static class Memory {
        private int windowSize = 5;
        private int sessionTtlSeconds = 3600;

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }

        public int getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(int sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }
    }
}
