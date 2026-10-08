package com.vivekkrishnan.fitbook.exception;

// Thrown when an authenticated user acts on a resource they don't own
// (e.g. cancelling someone else's appointment). Maps to HTTP 403 once the
// global exception handler is built (Phase 4).
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
