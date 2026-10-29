package com.vivekkrishnan.fitbook.dto;

import jakarta.validation.constraints.NotBlank;

// The one coarse-grained payload that crosses the boundary to the notification
// service: everything the receiver needs for a single book/cancel event, so the
// Gateway makes one remote call per event instead of several chatty ones.
// idempotencyKey is deterministic per event (e.g. BOOKED-12) and is reused on every
// retry, so the receiver can recognise and ignore duplicates.
public record NotificationRequestDTO(
        @NotBlank String idempotencyKey,
        @NotBlank String recipient,
        @NotBlank String type,
        @NotBlank String message
) {
    public static final String TYPE_BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String TYPE_BOOKING_CANCELLED = "BOOKING_CANCELLED";
}
