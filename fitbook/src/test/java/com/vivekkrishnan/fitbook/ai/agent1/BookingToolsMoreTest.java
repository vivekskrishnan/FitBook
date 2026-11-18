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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// More agent-tool coverage (see BookingToolsTest for the confirm-before-write guards): the cancel
// flow, discarding a proposal, rejecting bad slots, and the read-only tools.
@SpringBootTest(properties = "fitbook.reminders.enabled=false")
class BookingToolsMoreTest {

    private static final long KAI = 1L;

    @Autowired SlotsService slotsService;
    @Autowired AppointmentsRepository appointmentsRepository;
    @Autowired AppointmentFacade appointmentFacade;
    @Autowired PendingActionStore pendingStore;
    @Autowired JdbcTemplate jdbc;

    private Long openSlotId;
    private Long bookedSlotId;
    private final LocalDateTime openStart = LocalDateTime.now().plusYears(3);

    @BeforeEach
    void seed() {
        openSlotId = insertSlot(openStart, "OPEN");
        bookedSlotId = insertSlot(LocalDateTime.now().plusYears(3).plusDays(1), "BOOKED");
    }

    @AfterEach
    void cleanUp() {
        pendingStore.clear(KAI);
        for (Long slot : new Long[]{openSlotId, bookedSlotId}) {
            jdbc.update("DELETE FROM appointments WHERE slot_id = ?", slot);
            jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", slot);
        }
    }

    private Long insertSlot(LocalDateTime start, String status) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status) "
                            + "VALUES (1, 1, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, start);
            ps.setObject(2, start.plusHours(1));
            ps.setString(3, status);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private BookingTools tools(String turnId) {
        return new BookingTools(KAI, turnId, slotsService, appointmentsRepository, appointmentFacade, pendingStore);
    }

    private Long appointmentFor(Long slotId) {
        return jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);
    }

    @Test
    void cancelFlowCancelsTheUsersOwnAppointmentInALaterTurn() {
        tools("t1").proposeBooking(openSlotId);
        tools("t2").confirmPendingAction();
        Long appointmentId = appointmentFor(openSlotId);

        String proposal = tools("t3").proposeCancellation(appointmentId);
        assertThat(proposal).contains("NOTHING IS CANCELLED YET");
        assertThat(statusOf(appointmentId)).isEqualTo("BOOKED");   // proposing changed nothing

        assertThat(tools("t4").confirmPendingAction()).startsWith("Cancelled");
        assertThat(statusOf(appointmentId)).isEqualTo("CANCELLED");
    }

    @Test
    void cancelConfirmInTheSameTurnIsRefused() {
        tools("t1").proposeBooking(openSlotId);
        tools("t2").confirmPendingAction();
        Long appointmentId = appointmentFor(openSlotId);

        BookingTools sameTurn = tools("t3");
        sameTurn.proposeCancellation(appointmentId);

        assertThat(sameTurn.confirmPendingAction()).startsWith("Refused");
        assertThat(statusOf(appointmentId)).isEqualTo("BOOKED");
    }

    @Test
    void cannotProposeCancellingAnAlreadyCancelledAppointment() {
        tools("t1").proposeBooking(openSlotId);
        tools("t2").confirmPendingAction();
        Long appointmentId = appointmentFor(openSlotId);
        tools("t3").proposeCancellation(appointmentId);
        tools("t4").confirmPendingAction();

        assertThat(tools("t5").proposeCancellation(appointmentId)).contains("already CANCELLED");
    }

    @Test
    void discardingAProposalMeansNothingCanBeConfirmed() {
        tools("t1").proposeBooking(openSlotId);

        assertThat(tools("t2").cancelPendingAction()).contains("discarded");
        assertThat(tools("t3").confirmPendingAction()).contains("nothing pending");
        assertThat(appointmentsRepository.countBySlot(openSlotId)).isZero();
    }

    @Test
    void aNewerProposalReplacesTheOlderOne() {
        Long otherSlot = insertSlot(LocalDateTime.now().plusYears(3).plusDays(2), "OPEN");
        try {
            tools("t1").proposeBooking(openSlotId);
            tools("t2").proposeBooking(otherSlot);

            tools("t3").confirmPendingAction();

            assertThat(appointmentsRepository.countBySlot(otherSlot)).isEqualTo(1);
            assertThat(appointmentsRepository.countBySlot(openSlotId)).isZero();
        } finally {
            jdbc.update("DELETE FROM appointments WHERE slot_id = ?", otherSlot);
            jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", otherSlot);
        }
    }

    @Test
    void proposingAnUnavailableSlotIsRejectedAndStoresNothing() {
        assertThat(tools("t1").proposeBooking(bookedSlotId)).contains("no longer available");
        assertThat(pendingStore.get(KAI)).isNull();
    }

    @Test
    void proposingAMissingSlotIsRejected() {
        assertThat(tools("t1").proposeBooking(999_999_999L)).contains("does not exist");
        assertThat(pendingStore.get(KAI)).isNull();
    }

    @Test
    void checkAvailabilityListsOpenSlotsAndFiltersByDate() {
        String onTheDay = tools("t1").checkAvailability(null, null, openStart.toLocalDate().toString());
        assertThat(onTheDay).contains("slotId=" + openSlotId);

        String otherDay = tools("t1").checkAvailability(null, null, LocalDate.of(2000, 1, 1).toString());
        assertThat(otherDay).isEqualTo("No open slots match those filters.");
    }

    @Test
    void checkAvailabilityRejectsABadDate() {
        assertThat(tools("t1").checkAvailability(null, null, "next tuesday")).contains("Invalid date");
    }

    @Test
    void listMyAppointmentsShowsOnlyTheCurrentUsersAppointments() {
        tools("t1").proposeBooking(openSlotId);
        tools("t2").confirmPendingAction();

        String mine = tools("t3").listMyAppointments();
        assertThat(mine).contains("appointmentId=" + appointmentFor(openSlotId));

        BookingTools nya = new BookingTools(4L, "t4", slotsService, appointmentsRepository, appointmentFacade, pendingStore);
        assertThat(nya.listMyAppointments()).doesNotContain("appointmentId=" + appointmentFor(openSlotId));
    }

    @Test
    void listTrainersAndServicesReturnsIdsForTheModel() {
        String result = tools("t1").listTrainersAndServices();

        assertThat(result).contains("Trainers:").contains("Services:").contains("Weight training").contains("Pilates");
    }

    private String statusOf(Long appointmentId) {
        return jdbc.queryForObject("SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId);
    }
}
