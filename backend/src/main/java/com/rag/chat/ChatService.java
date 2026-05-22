package com.rag.chat;

import com.rag.chat.entity.ChatHistoryEntity;
import com.rag.chat.memory.RedisChatMemoryStore;
import com.rag.chat.repository.ChatHistoryRepository;
import com.rag.config.RagProperties;
import com.rag.document.entity.DocumentEntity;
import com.rag.document.repository.DocumentRepository;
import com.rag.embedding.EmbeddingService;
import com.rag.retrieval.BM25KeywordService;
import com.rag.vector.MilvusService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final DocumentRepository documentRepository;
    private final ChatLanguageModel chatModel;
    private final RagProperties ragProperties;
    private final RedisChatMemoryStore chatMemoryStore;
    private final BM25KeywordService bm25Service;

    public ChatService(
            EmbeddingService embeddingService,
            MilvusService milvusService,
            ChatHistoryRepository chatHistoryRepository,
            DocumentRepository documentRepository,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${chat.model}") String modelName,
            @Value("${chat.temperature}") Double temperature,
            RagProperties ragProperties,
            RedisChatMemoryStore chatMemoryStore,
            BM25KeywordService bm25Service) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.documentRepository = documentRepository;
        this.ragProperties = ragProperties;
        this.chatMemoryStore = chatMemoryStore;
        this.bm25Service = bm25Service;
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    public ChatResult chat(String sessionId, String question) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                    .id(sessionId)
                    .maxMessages(ragProperties.getMemory().getWindowSize() * 2)
                    .chatMemoryStore(chatMemoryStore)
                    .build();

            List<ChatMessage> history = memory.messages();

            String searchQuestion = rewriteQuestion(history, question);

            float[] queryVector = embeddingService.embed(searchQuestion);
            List<EmbeddingMatch<TextSegment>> vectorMatches = milvusService.search(
                    queryVector, ragProperties.getRetrieval().getVectorTopK() * 2);

            List<BM25KeywordService.KeywordHit> bm25Hits = bm25Service.search(
                    searchQuestion, ragProperties.getRetrieval().getBm25TopK() * 2);

            List<EmbeddingMatch<TextSegment>> merged = mergeResults(
                    vectorMatches, bm25Hits, ragProperties.getRetrieval().getRrfTopK());

            List<EmbeddingMatch<TextSegment>> relevantMatches = merged.stream()
                    .filter(m -> m.score() >= MIN_SCORE_THRESHOLD)
                    .collect(Collectors.toList());

            if (relevantMatches.isEmpty()) {
                memory.add(UserMessage.from(question));
                memory.add(AiMessage.from(NO_CONTEXT_ANSWER));
                chatHistoryRepository.save(new ChatHistoryEntity(question, NO_CONTEXT_ANSWER, "deepseek-chat", sessionId));
                return new ChatResult(NO_CONTEXT_ANSWER, List.of(), sessionId);
            }

            String context = relevantMatches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n\n"));

            String historyText = formatHistory(history);
            String prompt = buildPrompt(context, historyText, question);
            String answer = chatModel.generate(prompt);

            List<SourceReference> sources = relevantMatches.stream()
                    .map(m -> {
                        String docId = m.embedded().metadata().get("docId");
                        String docName = "";
                        if (docId != null) {
                            docName = documentRepository.findById(docId)
                                    .map(DocumentEntity::getName)
                                    .orElse("");
                        }
                        return new SourceReference(docId, docName, m.embedded().text(), m.score());
                    })
                    .collect(Collectors.toList());

            memory.add(UserMessage.from(question));
            memory.add(AiMessage.from(answer));
            chatHistoryRepository.save(new ChatHistoryEntity(question, answer, "deepseek-chat", sessionId));

            return new ChatResult(answer, sources, sessionId);
        } catch (RuntimeException e) {
            throw new IllegalStateException("对话服务暂时不可用，请稍后重试");
        }
    }

    private List<EmbeddingMatch<TextSegment>> mergeResults(
            List<EmbeddingMatch<TextSegment>> vectorMatches,
            List<BM25KeywordService.KeywordHit> bm25Hits,
            int topK) {

        Map<String, EmbeddingMatch<TextSegment>> dedup = new LinkedHashMap<>();
        double k = 60.0;

        for (int i = 0; i < vectorMatches.size(); i++) {
            EmbeddingMatch<TextSegment> m = vectorMatches.get(i);
            String docId = m.embedded().metadata().get("docId");
            String key = docId + ":" + m.embedded().text();
            double rrf = 1.0 / (k + i + 1);
            dedup.put(key, new EmbeddingMatch<>(rrf, key, m.embedding(), m.embedded()));
        }

        for (int i = 0; i < bm25Hits.size(); i++) {
            BM25KeywordService.KeywordHit hit = bm25Hits.get(i);
            String key = hit.docId() + ":" + hit.content();
            if (!dedup.containsKey(key)) {
                double rrf = 1.0 / (k + i + 1);
                TextSegment segment = TextSegment.from(hit.content(), Metadata.from("docId", hit.docId()));
                Embedding fake = Embedding.from(new float[0]);
                dedup.put(key, new EmbeddingMatch<>(rrf, key, fake, segment));
            }
        }

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>(dedup.values());
        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (result.size() > topK) {
            return result.subList(0, topK);
        }
        return result;
    }

    private String rewriteQuestion(List<ChatMessage> history, String question) {
        if (history.isEmpty()) {
            return question;
        }
        String historyText = formatHistory(history);
        String rewritePrompt = String.format(
                "根据以下对话历史，将用户的最新问题改写为一个独立、完整的问题。\n" +
                "如果最新问题本身已经完整，则直接返回原问题。\n\n" +
                "对话历史：\n%s\n\n最新问题：%s\n\n改写后的独立问题：",
                historyText, question);
        return chatModel.generate(rewritePrompt);
    }

    private String formatHistory(List<ChatMessage> history) {
        return history.stream()
                .map(msg -> {
                    if (msg instanceof UserMessage) return "用户：" + ((UserMessage) msg).singleText();
                    if (msg instanceof AiMessage) return "助手：" + ((AiMessage) msg).text();
                    return "";
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildPrompt(String context, String historyText, String question) {
        return String.format(
                "基于以下内容回答问题，结合对话历史理解上下文。\n" +
                "如果内容中没有相关信息，请说明无法回答。\n\n" +
                "对话历史：\n%s\n\n检索内容：\n%s\n\n问题：%s\n\n回答：",
                historyText, context, question);
    }

    public static class ChatResult {
        private final String answer;
        private final List<SourceReference> sources;
        private final String sessionId;

        public ChatResult(String answer, List<SourceReference> sources, String sessionId) {
            this.answer = answer;
            this.sources = sources;
            this.sessionId = sessionId;
        }

        public String getAnswer() { return answer; }
        public List<SourceReference> getSources() { return sources; }
        public String getSessionId() { return sessionId; }
    }

    public static class SourceReference {
        private final String docId;
        private final String docName;
        private final String content;
        private final double score;

        public SourceReference(String docId, String docName, String content, double score) {
            this.docId = docId;
            this.docName = docName;
            this.content = content;
            this.score = score;
        }

        public String getDocId() { return docId; }
        public String getDocName() { return docName; }
        public String getContent() { return content; }
        public double getScore() { return score; }
    }
}