package com.vivekkrishnan.fitbook.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs without Gemini: only checks how the knowledge file is chunked.
class KnowledgeIngestorTest {

    @Test
    void splitsFaqIntoOneDocumentPerSection() throws Exception {
        String markdown = new ClassPathResource("knowledge/fitbook-faq.md").getContentAsString(StandardCharsets.UTF_8);

        List<Document> docs = KnowledgeIngestor.split(markdown);

        assertThat(docs).hasSizeGreaterThanOrEqualTo(8);
        assertThat(docs).extracting(d -> d.getMetadata().get("section"))
                .contains("Cancellation policy", "Services and pricing", "Studio hours");
    }

    @Test
    void eachChunkCarriesItsSectionTitleAndContent() {
        List<Document> docs = KnowledgeIngestor.split("# Title\n\n## Hours\nOpen 6 to 9.\n\n## Price\n$45 per session.\n");

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getText()).isEqualTo("Hours: Open 6 to 9.");
        assertThat(docs.get(1).getText()).isEqualTo("Price: $45 per session.");
    }
}
