package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.dto.NotificationResponseDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Stands in for an external messaging system (email/SMS/Slack). It lives in the same
// project but is only ever called over HTTP by NotificationGateway, so it is still a
// real distribution boundary. It logs and stores what it receives and returns success;
// nothing is actually sent. Stored in memory only - lost on restart, which is fine for
// a mock.
//
// notification.mock.delay-ms / notification.mock.fail let us simulate a slow or down
// service for demos and tests without stopping the app.
@RestController
@RequestMapping("/mock-notifications")
public class MockNotificationController {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);

    // Keyed by idempotencyKey: a repeated key is a retry of something already received.
    private final Map<String, NotificationRequestDTO> received = new ConcurrentHashMap<>();

    private final long delayMs;
    private final boolean fail;

    public MockNotificationController(
            @Value("${notification.mock.delay-ms:0}") long delayMs,
            @Value("${notification.mock.fail:false}") boolean fail
    ) {
        this.delayMs = delayMs;
        this.fail = fail;
    }

    @PostMapping
    public ResponseEntity<NotificationResponseDTO> receive(@Valid @RequestBody NotificationRequestDTO request) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (fail) {
            log.warn("Mock notification service simulating failure: key={}", request.idempotencyKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        boolean isNew = received.putIfAbsent(request.idempotencyKey(), request) == null;
        if (isNew) {
            log.info("Notification received: type={} recipient={} key={}",
                    request.type(), request.recipient(), request.idempotencyKey());
        } else {
            log.info("Duplicate notification ignored: key={}", request.idempotencyKey());
        }

        String status = isNew ? NotificationResponseDTO.STATUS_ACCEPTED : NotificationResponseDTO.STATUS_DUPLICATE;
        return ResponseEntity.ok(new NotificationResponseDTO(status, request.idempotencyKey(), LocalDateTime.now()));
    }

    // Everything received so far - handy for tests and the demo.
    @GetMapping
    public List<NotificationRequestDTO> list() {
        return List.copyOf(received.values());
    }

    // Local handler so a bad payload is a 400, not the app-wide catch-all's 500 HTML page.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid notification request"));
    }
}
