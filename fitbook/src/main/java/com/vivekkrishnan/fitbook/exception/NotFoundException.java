package com.vivekkrishnan.fitbook.exception;

// Thrown when a requested resource (e.g. an appointment id) doesn't exist.
// Maps to HTTP 404 once the global exception handler is built (Phase 4).
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
