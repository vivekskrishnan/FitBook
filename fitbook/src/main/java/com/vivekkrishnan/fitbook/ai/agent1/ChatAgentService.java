package com.vivekkrishnan.fitbook.ai.agent1;

import com.vivekkrishnan.fitbook.ai.LlmRetry;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.service.AppointmentFacade;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

// Agent 1 (conversational). A ChatClient with a system prompt, tool calling (BookingTools) and
// per-user multi-turn memory. The model decides which tools to call and in what order
// (for example listTrainersAndServices -> checkAvailability -> proposeBooking).
@Service
public class ChatAgentService {

    private static final String SYSTEM_PROMPT = """
            You are the FitBook booking assistant for a fitness studio. Today's date is %s.
            Help the logged-in customer find open slots, view their appointments, and book or
            cancel. Use the tools; never invent slots, ids, prices or appointments.

            Rules for changing anything:
            - Booking or cancelling is always two steps. First call proposeBooking or
              proposeCancellation, show the user the summary, and ask "Shall I go ahead?".
            - Call confirmPendingAction ONLY after the user has said yes in their latest message.
              Never propose and confirm in the same turn.
            - When your previous message asked "Shall I go ahead?" and the user replies yes (or ok,
              sure, confirm), your NEXT action must be to call confirmPendingAction. Do not propose
              again and do not ask again.
            - If the user says no or changes their mind, call cancelPendingAction.
            - If a slot is taken or booking fails, say so and offer other open slots with
              checkAvailability.
            - For "earliest" or "soonest" requests, call checkAvailability (results are sorted
              earliest first) and propose the first match.
            - You only act for the current user. Be short and friendly. You cannot take payments
              or give medical advice.
            """;

    private final ChatClient chatClient;
    private final SlotsService slotsService;
    private final AppointmentsRepository appointmentsRepository;
    private final AppointmentFacade appointmentFacade;
    private final PendingActionStore pendingStore;
    private final LlmRetry retry;

    public ChatAgentService(ChatClient.Builder chatClientBuilder, SlotsService slotsService,
                            AppointmentsRepository appointmentsRepository, AppointmentFacade appointmentFacade,
                            PendingActionStore pendingStore, LlmRetry retry) {
        this.slotsService = slotsService;
        this.appointmentsRepository = appointmentsRepository;
        this.appointmentFacade = appointmentFacade;
        this.pendingStore = pendingStore;
        this.retry = retry;

        // Memory: the last 20 messages per conversation id (we use the user id), held in memory.
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String chat(Long userId, String message) {
        String turnId = UUID.randomUUID().toString();
        BookingTools tools = new BookingTools(userId, turnId, slotsService, appointmentsRepository,
                appointmentFacade, pendingStore);
        String system = SYSTEM_PROMPT.formatted(LocalDate.now());
        return retry.call(() -> chatClient.prompt()
                .system(system)
                .user(message)
                .tools(tools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(userId)))
                .call()
                .content());
    }
}
