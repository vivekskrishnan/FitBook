package com.vivekkrishnan.fitbook.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRetryTest {

    @Test
    void retriesTransientFailuresThenSucceeds() {
        LlmRetry retry = new LlmRetry(3, 1);
        AtomicInteger calls = new AtomicInteger();

        String result = retry.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("503 high demand");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        LlmRetry retry = new LlmRetry(2, 1);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("503");
        })).hasMessage("503");
        assertThat(calls.get()).isEqualTo(2);
    }
}
