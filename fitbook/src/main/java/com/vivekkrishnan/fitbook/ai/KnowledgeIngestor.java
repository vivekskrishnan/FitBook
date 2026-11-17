package com.vivekkrishnan.fitbook.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Loads the hand-written FAQ (resources/knowledge/fitbook-faq.md) into the vector store.
// Chunking is by "## " section: each section is a short, self-contained policy, so one
// section = one retrievable document. Ingestion is lazy and idempotent: it runs on the
// first question, not at startup, so the app (and its tests) boot even if the Gemini
// embedding call is slow or rate-limited, and a failed attempt is simply retried next time.
@Component
public class KnowledgeIngestor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestor.class);

    private final VectorStore vectorStore;
    private final Resource knowledgeFile;
    private volatile boolean ingested = false;

    public KnowledgeIngestor(
            VectorStore vectorStore,
            @Value("classpath:knowledge/fitbook-faq.md") Resource knowledgeFile
    ) {
        this.vectorStore = vectorStore;
        this.knowledgeFile = knowledgeFile;
    }

    public synchronized void ensureIngested() {
        if (ingested) {
            return;
        }
        try {
            String markdown = knowledgeFile.getContentAsString(StandardCharsets.UTF_8);
            List<Document> documents = split(markdown);
            vectorStore.add(documents);
            ingested = true;
            log.info("Knowledge base ingested: {} sections embedded into the vector store", documents.size());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the knowledge file", e);
        }
    }

    // Package-private so it can be unit-tested without calling the embedding model.
    static List<Document> split(String markdown) {
        List<Document> documents = new ArrayList<>();
        String[] parts = markdown.split("(?m)^## ");
        for (int i = 1; i < parts.length; i++) {   // parts[0] is the title before the first section
            String part = parts[i].strip();
            int newline = part.indexOf('\n');
            String title = newline < 0 ? part : part.substring(0, newline).strip();
            String body = newline < 0 ? "" : part.substring(newline + 1).strip();
            documents.add(new Document(title + ": " + body.replaceAll("\\s*\\n\\s*", " "),
                    Map.of("section", title)));
        }
        return documents;
    }
}
