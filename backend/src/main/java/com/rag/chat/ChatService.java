package com.rag.chat;

import com.rag.chat.entity.ChatHistoryEntity;
import com.rag.chat.repository.ChatHistoryRepository;
import com.rag.config.RagProperties;
import com.rag.embedding.EmbeddingService;
import com.rag.vector.MilvusService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String NO_CONTEXT_ANSWER = "当前知识库中不具备足够依据回答该问题";
    private static final double MIN_SCORE_THRESHOLD = 0.3;

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatLanguageModel chatModel;
    private final RagProperties ragProperties;

    public ChatService(
            EmbeddingService embeddingService,
            MilvusService milvusService,
            ChatHistoryRepository chatHistoryRepository,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${chat.model}") String modelName,
            @Value("${chat.temperature}") Double temperature,
            RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ragProperties = ragProperties;
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    public ChatResult chat(String question) {
        float[] queryVector = embeddingService.embed(question);
        List<EmbeddingMatch<TextSegment>> matches = milvusService.search(
                queryVector, ragProperties.getRetrieval().getVectorTopK());

        List<EmbeddingMatch<TextSegment>> relevantMatches = matches.stream()
                .filter(m -> m.score() >= MIN_SCORE_THRESHOLD)
                .collect(Collectors.toList());

        if (relevantMatches.isEmpty()) {
            ChatHistoryEntity history = new ChatHistoryEntity(question, NO_CONTEXT_ANSWER, "deepseek-chat");
            chatHistoryRepository.save(history);
            return new ChatResult(NO_CONTEXT_ANSWER, List.of());
        }

        String context = relevantMatches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n"));

        String prompt = buildPrompt(context, question);
        String answer = chatModel.generate(prompt);

        List<SourceReference> sources = relevantMatches.stream()
                .map(m -> new SourceReference(m.embedded().text(), m.score()))
                .collect(Collectors.toList());

        ChatHistoryEntity history = new ChatHistoryEntity(question, answer, "deepseek-chat");
        chatHistoryRepository.save(history);

        return new ChatResult(answer, sources);
    }

    private String buildPrompt(String context, String question) {
        return String.format(
                "基于以下内容回答问题，如果内容中没有相关信息，请说明无法回答。\n\n" +
                "内容：\n%s\n\n" +
                "问题：%s\n\n" +
                "回答：",
                context, question);
    }

    public static class ChatResult {
        private final String answer;
        private final List<SourceReference> sources;

        public ChatResult(String answer, List<SourceReference> sources) {
            this.answer = answer;
            this.sources = sources;
        }

        public String getAnswer() { return answer; }
        public List<SourceReference> getSources() { return sources; }
    }

    public static class SourceReference {
        private final String content;
        private final double score;

        public SourceReference(String content, double score) {
            this.content = content;
            this.score = score;
        }

        public String getContent() { return content; }
        public double getScore() { return score; }
    }
}