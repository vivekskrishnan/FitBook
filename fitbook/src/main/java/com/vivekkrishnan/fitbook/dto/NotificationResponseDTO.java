package com.vivekkrishnan.fitbook.dto;

import java.time.LocalDateTime;

// Reply from the notification service. A repeated idempotencyKey is answered with
// DUPLICATE (still a success), so a retry after a lost response is harmless.
public record NotificationResponseDTO(
        String status,
        String idempotencyKey,
        LocalDateTime receivedAt
) {
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_DUPLICATE = "DUPLICATE";
}
