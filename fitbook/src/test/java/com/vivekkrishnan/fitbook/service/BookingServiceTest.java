package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers BookingService.book(): a plain OPEN-slot booking succeeds, and booking
// an already-BOOKED slot is rejected. See BookingServiceConcurrencyTest for the
// simultaneous-threads version of the second case.
@SpringBootTest
class BookingServiceTest {

    private static final long CUSTOMER_ID = 1L; // kai.smith@example.com, seed.sql
    private static final long OTHER_CUSTOMER_ID = 4L; // nya.smith@example.com, seed.sql

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
    void bookingAnOpenSlotSucceeds() {
        AppointmentDTO appointment = bookingService.book(slotId, CUSTOMER_ID);

        assertThat(appointment.status()).isEqualTo("BOOKED");
        String slotStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId);
        assertThat(slotStatus).isEqualTo("BOOKED");
    }

    @Test
    void bookingAnAlreadyBookedSlotIsRejected() {
        bookingService.book(slotId, CUSTOMER_ID);

        assertThatThrownBy(() -> bookingService.book(slotId, OTHER_CUSTOMER_ID))
                .isInstanceOf(SlotConflictException.class);

        Integer appointmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Integer.class, slotId);
        assertThat(appointmentCount).as("only the first booking should exist").isEqualTo(1);
    }
}
