package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers ProviderSlotService.createSlot() input validation (plan section 8/10:
// "invalid slot time rejected with 400"). ValidationException carries
// @ResponseStatus(BAD_REQUEST) - see exception/ValidationException.java - so any
// caller that lets it propagate uncaught gets a real HTTP 400 via
// GlobalExceptionHandler. ProviderController itself catches it instead, to
// redirect back to the dashboard with a flash message for a friendlier UX on
// the create-slot form - see ProviderController.createSlot.
@SpringBootTest
class ProviderSlotServiceTest {

    private static final long TRAINER_USER_ID = 2L; // lloyd.garmadon@example.com, seed.sql

    @Autowired
    private ProviderSlotService providerSlotService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanFarFutureSlots() {
        jdbcTemplate.update("DELETE FROM availability_slots WHERE start_time > NOW() + INTERVAL 1 YEAR + INTERVAL 200 DAY "
                + "AND slot_id NOT IN (SELECT slot_id FROM appointments)");
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusYears(1);

        assertThatThrownBy(() -> providerSlotService.createSlot(TRAINER_USER_ID, start, start.minusMinutes(1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void endTimeEqualToStartTimeIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusYears(1);

        assertThatThrownBy(() -> providerSlotService.createSlot(TRAINER_USER_ID, start, start))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void pastStartTimeIsRejected() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> providerSlotService.createSlot(TRAINER_USER_ID, start, start.plusHours(1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void overlappingSlotIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusYears(2).withNano(0).withSecond(0);
        providerSlotService.createSlot(TRAINER_USER_ID, start, start.plusHours(1));

        assertThatThrownBy(() -> providerSlotService.createSlot(TRAINER_USER_ID, start.plusMinutes(30), start.plusMinutes(90)))
                .isInstanceOf(com.vivekkrishnan.fitbook.exception.SlotConflictException.class);
    }
}
