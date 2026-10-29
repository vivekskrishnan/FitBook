package com.vivekkrishnan.fitbook.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Liveness + dependency check for Docker, CI and the demo. Public (see SecurityConfig).
// 200 when the app can run a trivial query against MySQL, 503 when it cannot, so a
// container healthcheck can tell "process up" from "actually able to serve".
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", "UP");
            body.put("database", "UP");
            body.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            log.error("Health check failed: database unreachable", e);
            body.put("status", "DOWN");
            body.put("database", "DOWN");
            body.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
