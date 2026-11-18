package com.vivekkrishnan.fitbook.ai.rag;

import com.vivekkrishnan.fitbook.ai.LlmRetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The RAG pipeline with a mocked vector store and a mocked ChatModel (no Gemini, no database):
// retrieval results end up in the prompt, sources are reported, and lazy ingestion behaves.
class KnowledgeServiceTest {

    private VectorStore vectorStore;
    private final List<Prompt> prompts = new ArrayList<>();
    private KnowledgeService service;
    private KnowledgeIngestor ingestor;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Cancel 24 hours ahead."))));
        });
        ingestor = new KnowledgeIngestor(vectorStore, new ClassPathResource("knowledge/fitbook-faq.md"));
        service = new KnowledgeService(ChatClient.builder(model), vectorStore, ingestor, new LlmRetry(1, 1));
    }

    private void retrievalReturns(Document... docs) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docs));
    }

    @Test
    void answersFromTheRetrievedSectionAndReportsItAsTheSource() {
        retrievalReturns(new Document("Cancellation policy: cancel at least 24 hours before.",
                Map.of("section", "Cancellation policy")));

        KnowledgeService.Answer answer = service.ask("What is the cancellation policy?");

        assertThat(answer.answer()).isEqualTo("Cancel 24 hours ahead.");
        assertThat(answer.sources()).containsExactly("Cancellation policy");
    }

    @Test
    void putsTheRetrievedTextAndTheGroundingRulesIntoThePrompt() {
        retrievalReturns(new Document("Studio hours: open 6 AM to 9 PM.", Map.of("section", "Studio hours")));

        service.ask("When are you open?");

        String everything = prompts.get(0).getContents() + prompts.get(0).getInstructions();
        assertThat(everything).contains("Studio hours: open 6 AM to 9 PM.");   // retrieved context
        assertThat(everything).contains("ONLY");                               // answer-only-from-context rule
        assertThat(everything).contains("When are you open?");
    }

    @Test
    void reportsEachSourceOnlyOnce() {
        retrievalReturns(
                new Document("Services and pricing: part one.", Map.of("section", "Services and pricing")),
                new Document("Services and pricing: part two.", Map.of("section", "Services and pricing")),
                new Document("Trainers: Lloyd.", Map.of("section", "Trainers")));

        assertThat(service.ask("Who trains and what does it cost?").sources())
                .containsExactly("Services and pricing", "Trainers");
    }

    @Test
    void hasNoSourcesWhenNothingRelevantIsRetrieved() {
        retrievalReturns();

        assertThat(service.ask("Do you sell moon bases?").sources()).isEmpty();
    }

    @Test
    void ingestsTheFaqOnlyOnceAcrossManyQuestions() {
        retrievalReturns();

        service.ask("one");
        service.ask("two");
        service.ask("three");

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    void failedIngestionIsRetriedOnTheNextQuestion() {
        retrievalReturns();
        doThrow(new RuntimeException("embedding service 503")).doNothing().when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.ask("first")).hasMessageContaining("503");
        assertThat(service.ask("second").answer()).isEqualTo("Cancel 24 hours ahead.");

        verify(vectorStore, times(2)).add(anyList());
    }

    @Test
    void embedsOneDocumentPerFaqSection() {
        retrievalReturns();
        doNothing().when(vectorStore).add(anyList());

        ingestor.ensureIngested();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Document>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue()).hasSizeGreaterThanOrEqualTo(8);
    }
}
