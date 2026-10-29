package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Milestone 3 required test: the real HttpNotificationGateway is pointed at a closed
// port (nothing listens on 127.0.0.1:1), so every send fails. Booking and cancelling
// must still succeed and persist, and the failure must be counted.
@SpringBootTest(properties = {
        "notification.base-url=http://127.0.0.1:1",
        "notification.max-attempts=2",
        "notification.retry-backoff-ms=10",
        "notification.connect-timeout-ms=300"
})
class NotificationDownTest {

    private static final long CUSTOMER_ID = 1L;

    @Autowired
    private AppointmentFacade appointmentFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private Long slotId;

    @BeforeEach
    void seedOpenSlot() {
        LocalDateTime start = LocalDateTime.now().plusYears(1);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status) "
                            + "VALUES (1, 1, ?, ?, 'OPEN')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setObject(1, start);
            ps.setObject(2, start.plusHours(1));
            return ps;
        }, keyHolder);
        slotId = keyHolder.getKey().longValue();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM appointments WHERE slot_id = ?", slotId);
        jdbcTemplate.update("DELETE FROM availability_slots WHERE slot_id = ?", slotId);
    }

    @Test
    void bookingSucceedsWhenNotificationServiceIsDown() {
        double failedBefore = failedNotifications();

        AppointmentDTO appointment = appointmentFacade.book(slotId, CUSTOMER_ID);

        assertThat(appointment.status()).isEqualTo("BOOKED");
        assertThat(slotStatus()).isEqualTo("BOOKED");
        assertThat(appointmentStatus(appointment.appointmentId())).isEqualTo("BOOKED");
        assertThat(failedNotifications()).isEqualTo(failedBefore + 1);
    }

    @Test
    void cancelSucceedsWhenNotificationServiceIsDown() {
        AppointmentDTO booked = appointmentFacade.book(slotId, CUSTOMER_ID);
        double failedBefore = failedNotifications();

        AppointmentDTO cancelled = appointmentFacade.cancel(booked.appointmentId(), CUSTOMER_ID);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(appointmentStatus(booked.appointmentId())).isEqualTo("CANCELLED");
        assertThat(failedNotifications()).isEqualTo(failedBefore + 1);
    }

    private double failedNotifications() {
        return meterRegistry.counter("fitbook.notifications.failed").count();
    }

    private String slotStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId);
    }

    private String appointmentStatus(Long appointmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId);
    }
}
