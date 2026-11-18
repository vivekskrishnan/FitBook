package com.vivekkrishnan.fitbook.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// JSON endpoint for grounded FAQ Q&A (RAG). Any logged-in user may ask.
@RestController
@RequestMapping("/ai")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final KnowledgeService knowledgeService;

    public RagController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/ask")
    public ResponseEntity<?> ask(@RequestParam("q") String question) {
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide a question in ?q="));
        }
        try {
            return ResponseEntity.ok(knowledgeService.ask(question));
        } catch (RuntimeException e) {
            // Gemini can be rate-limited or overloaded (free tier); never leak internals.
            log.error("AI question failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "The AI service is unavailable right now. Please try again."));
        }
    }
}
