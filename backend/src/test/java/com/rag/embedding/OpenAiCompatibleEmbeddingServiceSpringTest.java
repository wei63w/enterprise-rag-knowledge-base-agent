package com.rag.embedding;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OpenAiCompatibleEmbeddingServiceSpringTest {

    @Test
    void springCanCreateEmbeddingService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmbeddingProperties.class, () -> {
                EmbeddingProperties properties = new EmbeddingProperties();
                properties.setApiKey("test-key");
                return properties;
            });
            context.register(OpenAiCompatibleEmbeddingService.class);

            context.refresh();

            assertNotNull(context.getBean(EmbeddingService.class));
        }
    }
}
