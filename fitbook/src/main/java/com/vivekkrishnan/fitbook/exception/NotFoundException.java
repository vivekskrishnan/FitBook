package com.vivekkrishnan.fitbook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when a requested resource (e.g. an appointment id) doesn't exist.
// @ResponseStatus gives a correct 404 (instead of a 500) even before the full
// GlobalExceptionHandler (Phase 4) exists to render a no-stack-trace error page.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
