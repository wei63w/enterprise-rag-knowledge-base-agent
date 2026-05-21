package com.rag.document.storage;

import java.io.InputStream;

public interface StorageService {

    String store(InputStream inputStream, StoredObject object);

    void delete(String objectKey);

    record StoredObject(String filename, String contentType, long size) {
    }
}
