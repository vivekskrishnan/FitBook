package com.vivekkrishnan.fitbook.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

// Grounded Q&A (retrieval-augmented generation): the question is embedded, the most similar
// FAQ sections are retrieved from the vector store by QuestionAnswerAdvisor and stuffed into
// the prompt, and the model is told to answer only from that context.
@Service
public class KnowledgeService {

    public record Answer(String answer, List<String> sources) {}

    private static final String SYSTEM_PROMPT = """
            You are the FitBook studio assistant. Answer the user's question using ONLY the
            context provided from the FitBook knowledge base. If the context does not contain the
            answer, say you don't have that information and suggest contacting the studio. Do not
            invent policies, prices or hours. Keep answers short and friendly.
            """;

    private final ChatClient chatClient;
    private final KnowledgeIngestor ingestor;
    private final LlmRetry retry;

    public KnowledgeService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
                            KnowledgeIngestor ingestor, LlmRetry retry) {
        this.ingestor = ingestor;
        this.retry = retry;
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.3).build())
                        .build())
                .build();
    }

    public Answer ask(String question) {
        retry.call(() -> { ingestor.ensureIngested(); return null; });
        ChatClientResponse response = retry.call(() -> chatClient.prompt().user(question).call().chatClientResponse());
        String text = response.chatResponse().getResult().getOutput().getText();
        return new Answer(text, sourcesOf(response));
    }

    @SuppressWarnings("unchecked")
    private static List<String> sourcesOf(ChatClientResponse response) {
        Object retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(retrieved instanceof List<?> docs)) {
            return List.of();
        }
        return ((List<Document>) docs).stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("section", "unknown")))
                .distinct()
                .toList();
    }
}
