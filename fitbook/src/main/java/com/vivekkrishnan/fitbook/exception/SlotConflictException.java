package com.vivekkrishnan.fitbook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when a slot can't be booked: already taken, or doesn't exist.
// @ResponseStatus gives a correct 409 even before the full GlobalExceptionHandler
// (Phase 4) exists; controllers that want a friendlier redirect still catch it directly.
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
