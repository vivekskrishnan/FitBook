package com.vivekkrishnan.fitbook.ai.agent2;

import com.vivekkrishnan.fitbook.ai.LlmRetry;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.gateway.NotificationGateway;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Agent 2 tests. The ChatModel is a Mockito mock, so nothing calls Gemini; the database and the
// reminder query are real. The gateway is a recording fake, so we can see exactly what was sent.
@SpringBootTest(properties = "fitbook.reminders.enabled=false")
class ReminderAgentTest {

    @Autowired AppointmentsRepository appointmentsRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<NotificationRequestDTO> sent = new ArrayList<>();
    private final NotificationGateway recordingGateway = request -> { sent.add(request); return true; };

    private Long soonSlot, laterSlot;

    @BeforeEach
    void seed() {
        soonSlot = insertBookedAppointment(LocalDateTime.now().plusHours(2), 1L);    // inside a 24h window
        laterSlot = insertBookedAppointment(LocalDateTime.now().plusDays(3), 4L);    // outside it
    }

    @AfterEach
    void cleanUp() {
        for (Long slot : List.of(soonSlot, laterSlot)) {
            jdbc.update("DELETE FROM appointments WHERE slot_id = ?", slot);
            jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", slot);
        }
    }

    private Long insertBookedAppointment(LocalDateTime start, long customerId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status) "
                            + "VALUES (1, 1, ?, ?, 'BOOKED')", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, start);
            ps.setObject(2, start.plusHours(1));
            return ps;
        }, keys);
        Long slotId = keys.getKey().longValue();
        jdbc.update("INSERT INTO appointments (slot_id, customer_id, service_id, status) VALUES (?, ?, 1, 'BOOKED')",
                slotId, customerId);
        return slotId;
    }

    private ReminderAgent agentWith(ChatModel model) {
        return new ReminderAgent(ChatClient.builder(model), appointmentsRepository, recordingGateway,
                new LlmRetry(1, 1));
    }

    private static ChatModel modelAnswering(String text) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return model;
    }

    private Long appointmentIdOf(Long slotId) {
        return jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);
    }

    @Test
    void sendsTheLlmWrittenReminderForAppointmentsInTheWindowOnly() {
        ReminderAgent agent = agentWith(modelAnswering("Hi Kai, see you soon for weight training!"));

        ReminderAgent.RunSummary summary = agent.run(Duration.ofHours(24));

        assertThat(summary.sent()).isEqualTo(1);
        assertThat(summary.llmWritten()).isEqualTo(1);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).idempotencyKey()).isEqualTo("REMINDER-" + appointmentIdOf(soonSlot));
        assertThat(sent.get(0).type()).isEqualTo(NotificationRequestDTO.TYPE_APPOINTMENT_REMINDER);
        assertThat(sent.get(0).recipient()).isEqualTo("kai.smith@example.com");
        assertThat(sent.get(0).message()).isEqualTo("Hi Kai, see you soon for weight training!");
    }

    @Test
    void stillSendsAReminderWhenTheLlmFails() {
        ChatModel broken = mock(ChatModel.class);
        when(broken.getOptions()).thenReturn(ChatOptions.builder().build());
        when(broken.call(any(Prompt.class))).thenThrow(new RuntimeException("503 high demand"));

        ReminderAgent.RunSummary summary = agentWith(broken).run(Duration.ofHours(24));

        assertThat(summary.sent()).isEqualTo(1);
        assertThat(summary.fallbackUsed()).isEqualTo(1);
        assertThat(sent.get(0).message()).contains("Hi Kai").contains("Weight training").contains("arrive 5 minutes early");
    }

    @Test
    void doesNotRemindTheSameAppointmentTwice() {
        ReminderAgent agent = agentWith(modelAnswering("Reminder text"));

        agent.run(Duration.ofHours(24));
        ReminderAgent.RunSummary second = agent.run(Duration.ofHours(24));

        assertThat(second.alreadyReminded()).isEqualTo(1);
        assertThat(second.sent()).isZero();
        assertThat(sent).hasSize(1);
    }

    @Test
    void widerWindowIncludesLaterAppointments() {
        ReminderAgent.RunSummary summary = agentWith(modelAnswering("Reminder text")).run(Duration.ofDays(5));

        assertThat(summary.found()).isEqualTo(2);
        assertThat(summary.sent()).isEqualTo(2);
    }

    @Test
    void failedDeliveryIsRetriedOnTheNextRun() {
        List<NotificationRequestDTO> attempts = new ArrayList<>();
        boolean[] up = {false};
        NotificationGateway flaky = request -> { attempts.add(request); return up[0]; };
        ReminderAgent agent = new ReminderAgent(ChatClient.builder(modelAnswering("Reminder text")),
                appointmentsRepository, flaky, new LlmRetry(1, 1));

        assertThat(agent.run(Duration.ofHours(24)).failed()).isEqualTo(1);
        up[0] = true;
        assertThat(agent.run(Duration.ofHours(24)).sent()).isEqualTo(1);
        assertThat(attempts).hasSize(2);
    }
}
