package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers BookingService.cancel(): owner-only enforcement plus the not-found and
// already-cancelled edge cases.
@SpringBootTest
class CancelServiceTest {

    private static final long OWNER_CUSTOMER_ID = 1L;
    private static final long OTHER_CUSTOMER_ID = 2L; // a seeded user id, not a customer role - fine for FK purposes

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(start.plusHours(1)));
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
    void ownerCancelSucceeds() {
        AppointmentDTO booked = bookingService.book(slotId, OWNER_CUSTOMER_ID);

        AppointmentDTO cancelled = bookingService.cancel(booked.appointmentId(), OWNER_CUSTOMER_ID);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void nonOwnerCancelThrowsForbidden() {
        AppointmentDTO booked = bookingService.book(slotId, OWNER_CUSTOMER_ID);

        assertThatThrownBy(() -> bookingService.cancel(booked.appointmentId(), OTHER_CUSTOMER_ID))
                .isInstanceOf(ForbiddenException.class);

        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id = ?", String.class, booked.appointmentId());
        assertThat(currentStatus).as("appointment should remain BOOKED after a rejected cancel").isEqualTo("BOOKED");
    }

    @Test
    void cancelUnknownAppointmentThrowsNotFound() {
        assertThatThrownBy(() -> bookingService.cancel(-1L, OWNER_CUSTOMER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelAlreadyCancelledThrowsConflict() {
        AppointmentDTO booked = bookingService.book(slotId, OWNER_CUSTOMER_ID);
        bookingService.cancel(booked.appointmentId(), OWNER_CUSTOMER_ID);

        assertThatThrownBy(() -> bookingService.cancel(booked.appointmentId(), OWNER_CUSTOMER_ID))
                .isInstanceOf(SlotConflictException.class);
    }
}
