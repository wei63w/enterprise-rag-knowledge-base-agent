package com.rag.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentTextParserTest {

    @Test
    void parsesPlainText() {
        DocumentTextParser parser = new DocumentTextParser();
        ByteArrayInputStream inputStream = new ByteArrayInputStream("hello parser".getBytes(StandardCharsets.UTF_8));

        String text = parser.parse(inputStream, "sample.txt");

        assertThat(text).contains("hello parser");
    }
}
