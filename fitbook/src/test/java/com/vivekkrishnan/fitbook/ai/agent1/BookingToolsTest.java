package com.vivekkrishnan.fitbook.ai.agent1;

import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.service.AppointmentFacade;
import com.vivekkrishnan.fitbook.service.SlotsService;
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

// Tests the agent's tools directly (no Gemini): the write path is the real transactional
// service, and confirm-before-write is enforced in code.
@SpringBootTest
class BookingToolsTest {

    private static final long KAI = 1L;
    private static final long NYA = 4L;

    @Autowired SlotsService slotsService;
    @Autowired AppointmentsRepository appointmentsRepository;
    @Autowired AppointmentFacade appointmentFacade;
    @Autowired PendingActionStore pendingStore;
    @Autowired JdbcTemplate jdbc;

    private Long slotId;

    @BeforeEach
    void seedSlot() {
        LocalDateTime start = LocalDateTime.now().plusYears(2);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status) "
                            + "VALUES (1, 1, ?, ?, 'OPEN')", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, start);
            ps.setObject(2, start.plusHours(1));
            return ps;
        }, keys);
        slotId = keys.getKey().longValue();
    }

    @AfterEach
    void cleanUp() {
        pendingStore.clear(KAI);
        pendingStore.clear(NYA);
        jdbc.update("DELETE FROM appointments WHERE slot_id = ?", slotId);
        jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", slotId);
    }

    private BookingTools tools(long userId, String turnId) {
        return new BookingTools(userId, turnId, slotsService, appointmentsRepository, appointmentFacade, pendingStore);
    }

    @Test
    void proposeAloneDoesNotBook() {
        tools(KAI, "t1").proposeBooking(slotId);

        assertThat(appointmentsRepository.countBySlot(slotId)).isZero();
    }

    @Test
    void confirmInSameTurnIsRefused() {
        BookingTools sameTurn = tools(KAI, "t1");
        sameTurn.proposeBooking(slotId);

        String result = sameTurn.confirmPendingAction();

        assertThat(result).startsWith("Refused");
        assertThat(appointmentsRepository.countBySlot(slotId)).isZero();
    }

    @Test
    void confirmInLaterTurnBooksThroughTheService() {
        tools(KAI, "t1").proposeBooking(slotId);

        String result = tools(KAI, "t2").confirmPendingAction();

        assertThat(result).startsWith("Booked");
        assertThat(appointmentsRepository.countBySlot(slotId)).isEqualTo(1);
    }

    @Test
    void agentCannotDoubleBookASlot() {
        tools(KAI, "t1").proposeBooking(slotId);
        tools(NYA, "t1").proposeBooking(slotId);
        assertThat(tools(KAI, "t2").confirmPendingAction()).startsWith("Booked");

        String second = tools(NYA, "t2").confirmPendingAction();

        assertThat(second).contains("could not be done");
        assertThat(appointmentsRepository.countBySlot(slotId)).isEqualTo(1);
    }

    @Test
    void confirmWithNothingPendingDoesNothing() {
        assertThat(tools(KAI, "t2").confirmPendingAction()).contains("nothing pending");
    }

    @Test
    void cannotProposeCancellingSomeoneElsesAppointment() {
        tools(KAI, "t1").proposeBooking(slotId);
        tools(KAI, "t2").confirmPendingAction();
        Long appointmentId = jdbc.queryForObject(
                "SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);

        String result = tools(NYA, "t3").proposeCancellation(appointmentId);

        assertThat(result).contains("no appointment");
        assertThat(pendingStore.get(NYA)).isNull();
    }
}
