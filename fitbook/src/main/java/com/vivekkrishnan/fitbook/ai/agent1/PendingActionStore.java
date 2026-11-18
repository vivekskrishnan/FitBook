package com.vivekkrishnan.fitbook.ai.agent1;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Confirm-before-write, enforced in code (not only in the prompt). A "propose" tool stores
// ONE pending action per user here; the "confirm" tool may only run it if it was proposed
// during an EARLIER chat request, i.e. the user had a turn in between to say yes.
// So even if the model tries propose + confirm in the same turn, the write is refused.
@Component
public class PendingActionStore {

    public enum Kind { BOOK, CANCEL }

    public record Pending(Kind kind, Long targetId, String summary, String turnId, Instant createdAt) {}

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<Long, Pending> pendingByUser = new ConcurrentHashMap<>();

    public void put(Long userId, Pending pending) {
        pendingByUser.put(userId, pending);   // a new proposal replaces any older one
    }

    // Returns the user's pending action, or null if none or expired.
    public Pending get(Long userId) {
        Pending pending = pendingByUser.get(userId);
        if (pending != null && pending.createdAt().plus(TTL).isBefore(Instant.now())) {
            pendingByUser.remove(userId);
            return null;
        }
        return pending;
    }

    public void clear(Long userId) {
        pendingByUser.remove(userId);
    }
}
