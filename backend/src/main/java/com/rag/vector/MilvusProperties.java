package com.rag.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String host = "localhost";
    private int port = 19530;
    private String collection = "document_chunks_v1";
    private int dimension = 1024;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }
}