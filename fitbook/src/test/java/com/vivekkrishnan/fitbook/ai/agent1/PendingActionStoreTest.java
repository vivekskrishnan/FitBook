package com.vivekkrishnan.fitbook.ai.agent1;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit tests (no Spring, no database) for the pending-proposal store behind confirm-before-write.
class PendingActionStoreTest {

    private static PendingActionStore.Pending pending(Long target, String turn, Instant createdAt) {
        return new PendingActionStore.Pending(PendingActionStore.Kind.BOOK, target, "summary", turn, createdAt);
    }

    @Test
    void storesAndReturnsAProposalPerUser() {
        PendingActionStore store = new PendingActionStore();
        store.put(1L, pending(10L, "t1", Instant.now()));

        assertThat(store.get(1L).targetId()).isEqualTo(10L);
        assertThat(store.get(2L)).isNull();   // another user sees nothing
    }

    @Test
    void aNewProposalReplacesTheOldOne() {
        PendingActionStore store = new PendingActionStore();
        store.put(1L, pending(10L, "t1", Instant.now()));
        store.put(1L, pending(20L, "t2", Instant.now()));

        assertThat(store.get(1L).targetId()).isEqualTo(20L);
    }

    @Test
    void clearRemovesTheProposal() {
        PendingActionStore store = new PendingActionStore();
        store.put(1L, pending(10L, "t1", Instant.now()));

        store.clear(1L);

        assertThat(store.get(1L)).isNull();
    }

    @Test
    void proposalsExpireAfterTenMinutes() {
        PendingActionStore store = new PendingActionStore();
        store.put(1L, pending(10L, "t1", Instant.now().minus(Duration.ofMinutes(11))));
        store.put(2L, pending(20L, "t1", Instant.now().minus(Duration.ofMinutes(9))));

        assertThat(store.get(1L)).isNull();       // expired
        assertThat(store.get(2L)).isNotNull();    // still valid
    }
}
