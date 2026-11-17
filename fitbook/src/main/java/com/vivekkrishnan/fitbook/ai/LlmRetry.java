package com.vivekkrishnan.fitbook.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

// The Gemini free tier regularly answers 503 "high demand" or 429 rate limits. Those are
// transient, so LLM calls get a few attempts with a doubling backoff before giving up
// (same idea as the notification gateway in Milestone 3). Used by the Q&A service and
// both agents.
@Component
public class LlmRetry {

    private static final Logger log = LoggerFactory.getLogger(LlmRetry.class);

    private final int maxAttempts;
    private final long backoffMs;

    public LlmRetry(
            @Value("${fitbook.ai.max-attempts:4}") int maxAttempts,
            @Value("${fitbook.ai.retry-backoff-ms:1500}") long backoffMs
    ) {
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public <T> T call(Supplier<T> llmCall) {
        RuntimeException last = null;
        long delay = backoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return llmCall.get();
            } catch (RuntimeException e) {
                last = e;
                log.warn("LLM call failed (attempt {}/{}): {}", attempt, maxAttempts, rootMessage(e));
                if (attempt < maxAttempts) {
                    sleep(delay);
                    delay *= 2;
                }
            }
        }
        throw last;
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
