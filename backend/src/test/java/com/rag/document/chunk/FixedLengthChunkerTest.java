package com.rag.document.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FixedLengthChunkerTest {

    @Test
    void splitsTextIntoFixedLengthChunksWithOverlap() {
        FixedLengthChunker chunker = new FixedLengthChunker(5, 2);

        List<String> chunks = chunker.split("abcdefghijkl");

        assertThat(chunks).containsExactly("abcde", "defgh", "ghijk", "jkl");
    }

    @Test
    void ignoresBlankText() {
        FixedLengthChunker chunker = new FixedLengthChunker(5, 2);

        List<String> chunks = chunker.split("   ");

        assertThat(chunks).isEmpty();
    }
}
