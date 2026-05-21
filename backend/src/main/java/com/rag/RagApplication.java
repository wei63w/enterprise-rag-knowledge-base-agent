package com.rag;

import com.rag.config.RagProperties;
import com.rag.embedding.EmbeddingProperties;
import com.rag.vector.MilvusProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, EmbeddingProperties.class, MilvusProperties.class})
public class RagApplication implements CommandLineRunner {
    // 直接从配置里读取
    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=====================================");
        System.out.println("真实数据库用户名：" + username);
        System.out.println("真实数据库密码：" + password);
        System.out.println("=====================================");
    }
}
