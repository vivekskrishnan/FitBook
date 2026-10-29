package com.vivekkrishnan.fitbook.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// UP against the real database, DOWN (503) when the query fails.
@SpringBootTest
class HealthControllerTest {

    @Autowired
    private HealthController healthController;

    @Test
    void reportsUpWith200WhenDatabaseIsReachable() {
        ResponseEntity<Map<String, Object>> response = healthController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP").containsEntry("database", "UP");
    }

    @Test
    void reportsDownWith503WhenDatabaseFails() {
        JdbcTemplate broken = mock(JdbcTemplate.class);
        when(broken.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        ResponseEntity<Map<String, Object>> response = new HealthController(broken).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN").containsEntry("database", "DOWN");
    }
}
