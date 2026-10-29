package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.gateway.NotificationGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Commit-then-notify: exactly one notification per successful book/cancel, with the
// right type and deterministic key, and none when the operation fails.
@SpringBootTest
class AppointmentFacadeNotificationTest {

    private static final long CUSTOMER_ID = 1L;

    @Autowired
    private AppointmentFacade appointmentFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private NotificationGateway notificationGateway;

    private Long slotId;

    @BeforeEach
    void seedOpenSlot() {
        when(notificationGateway.send(any())).thenReturn(true);
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
    void bookSendsConfirmationWithDeterministicKey() {
        AppointmentDTO appointment = appointmentFacade.book(slotId, CUSTOMER_ID);

        ArgumentCaptor<NotificationRequestDTO> captor = ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationGateway, times(1)).send(captor.capture());
        NotificationRequestDTO sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationRequestDTO.TYPE_BOOKING_CONFIRMED);
        assertThat(sent.idempotencyKey()).isEqualTo("BOOKED-" + appointment.appointmentId());
        assertThat(sent.recipient()).isNotBlank();
        assertThat(sent.message()).contains("confirmed");
    }

    @Test
    void cancelSendsCancellationWithDeterministicKey() {
        AppointmentDTO appointment = appointmentFacade.book(slotId, CUSTOMER_ID);

        appointmentFacade.cancel(appointment.appointmentId(), CUSTOMER_ID);

        ArgumentCaptor<NotificationRequestDTO> captor = ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationGateway, times(2)).send(captor.capture());
        NotificationRequestDTO cancellation = captor.getAllValues().get(1);
        assertThat(cancellation.type()).isEqualTo(NotificationRequestDTO.TYPE_BOOKING_CANCELLED);
        assertThat(cancellation.idempotencyKey()).isEqualTo("CANCELLED-" + appointment.appointmentId());
        assertThat(cancellation.message()).contains("cancelled");
    }

    @Test
    void noNotificationWhenBookingFails() {
        appointmentFacade.book(slotId, CUSTOMER_ID);
        clearInvocations(notificationGateway);

        assertThatThrownBy(() -> appointmentFacade.book(slotId, CUSTOMER_ID))
                .isInstanceOf(SlotConflictException.class);

        verify(notificationGateway, never()).send(any());
    }
}
