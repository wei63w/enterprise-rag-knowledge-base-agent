package com.rag;

import com.rag.config.RagProperties;
import com.rag.embedding.EmbeddingProperties;
import com.rag.vector.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, EmbeddingProperties.class, MilvusProperties.class})
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
