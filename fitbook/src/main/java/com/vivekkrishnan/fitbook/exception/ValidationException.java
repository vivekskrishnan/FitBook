package com.vivekkrishnan.fitbook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown for request input that fails a business rule (e.g. slot end time not
// after start time). @ResponseStatus gives a correct 400 even before the full
// GlobalExceptionHandler (Phase 4) exists.
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
