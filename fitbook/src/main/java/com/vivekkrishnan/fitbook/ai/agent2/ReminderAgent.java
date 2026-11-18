package com.vivekkrishnan.fitbook.ai.agent2;

import com.vivekkrishnan.fitbook.ai.LlmRetry;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.gateway.NotificationGateway;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository.ReminderTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Agent 2 (autonomous). Nobody asks it anything: a scheduler (or the trainer's manual trigger)
// calls run(). For each BOOKED appointment starting soon it asks Gemini to write a short
// personalized reminder, then sends it through the same NotificationGateway used for booking
// confirmations. That outbound message is the agent's real-world action.
@Service
public class ReminderAgent {

    public record RunSummary(int found, int sent, int alreadyReminded, int llmWritten, int fallbackUsed, int failed) {}

    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d 'at' h:mm a");

    private static final String SYSTEM_PROMPT = """
            You write short appointment reminders for FitBook, a fitness studio.
            Write 2 sentences at most, warm and personal, plain text only (no markdown, no emojis).
            Use ONLY the facts given: customer first name, service, trainer, time, price.
            Mention to arrive 5 minutes early. Do not invent anything else.
            """;

    private final ChatClient chatClient;
    private final AppointmentsRepository appointmentsRepository;
    private final NotificationGateway notificationGateway;
    private final LlmRetry retry;

    // Appointment ids already reminded since startup, so the hourly job does not call the
    // LLM again for the same appointment. The idempotency key REMINDER-<id> is the second line
    // of defence on the receiving side.
    private final Set<Long> reminded = ConcurrentHashMap.newKeySet();

    public ReminderAgent(ChatClient.Builder chatClientBuilder, AppointmentsRepository appointmentsRepository,
                         NotificationGateway notificationGateway, LlmRetry retry) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.appointmentsRepository = appointmentsRepository;
        this.notificationGateway = notificationGateway;
        this.retry = retry;
    }

    public synchronized RunSummary run(Duration window) {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderTarget> targets = appointmentsRepository.findUpcomingBooked(now, now.plus(window));
        int sent = 0, already = 0, llm = 0, fallback = 0, failed = 0;
        boolean llmDown = false;   // once the LLM has failed this run, stop trying for the rest

        for (ReminderTarget t : targets) {
            if (reminded.contains(t.appointmentId())) {
                already++;
                continue;
            }
            String message = null;
            if (!llmDown) {
                try {
                    message = retry.call(() -> chatClient.prompt().user(facts(t)).call().content());
                } catch (RuntimeException e) {
                    llmDown = true;
                    log.warn("Reminder LLM unavailable, using the fallback template for the rest of this run: {}",
                            e.getMessage());
                }
            }
            if (message != null && !message.isBlank()) {
                llm++;
            } else {
                message = fallbackMessage(t);
                fallback++;
            }

            boolean ok = notificationGateway.send(new NotificationRequestDTO(
                    "REMINDER-" + t.appointmentId(), t.customerEmail(),
                    NotificationRequestDTO.TYPE_APPOINTMENT_REMINDER, message.strip()));
            if (ok) {
                reminded.add(t.appointmentId());
                sent++;
                log.info("Reminder sent: appointmentId={}", t.appointmentId());
            } else {
                failed++;   // not marked as reminded, so the next run tries again
            }
        }
        RunSummary summary = new RunSummary(targets.size(), sent, already, llm, fallback, failed);
        log.info("Reminder run finished: {}", summary);
        return summary;
    }

    private static String facts(ReminderTarget t) {
        return "Customer first name: " + firstName(t.customerName()) + "\nService: " + t.serviceName()
                + "\nTrainer: " + t.trainerName() + "\nTime: " + t.startTime().format(WHEN)
                + "\nPrice: $" + t.price();
    }

    // Used when the LLM is down, so a reminder still goes out.
    static String fallbackMessage(ReminderTarget t) {
        return "Hi " + firstName(t.customerName()) + ", a reminder that your " + t.serviceName()
                + " session with " + t.trainerName() + " is on " + t.startTime().format(WHEN)
                + ". Please arrive 5 minutes early.";
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }
}
