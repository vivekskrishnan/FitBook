package com.vivekkrishnan.fitbook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when an authenticated user acts on a resource they don't own
// (e.g. cancelling someone else's appointment). @ResponseStatus gives a correct
// 403 even before the full GlobalExceptionHandler (Phase 4) exists.
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
