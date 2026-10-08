package com.vivekkrishnan.fitbook.exception;

// Thrown when a slot can't be booked: already taken, or doesn't exist.
// Maps to HTTP 409 once the global exception handler is built (Phase 4).
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
