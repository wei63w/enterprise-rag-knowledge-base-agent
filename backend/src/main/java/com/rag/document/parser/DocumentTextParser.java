package com.rag.document.parser;

import java.io.InputStream;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextParser {

    private final Tika tika = new Tika();

    public String parse(InputStream inputStream, String filename) {
        try {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new IllegalStateException("文档解析失败：" + filename, e);
        }
    }
}
