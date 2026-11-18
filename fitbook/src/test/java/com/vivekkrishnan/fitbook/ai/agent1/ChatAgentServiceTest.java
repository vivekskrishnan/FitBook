package com.vivekkrishnan.fitbook.ai.agent1;

import com.vivekkrishnan.fitbook.ai.LlmRetry;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.service.AppointmentFacade;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Agent 1's chat service with a MOCKED ChatModel (no Gemini): checks the prompt the model receives
// (system rules, today's date, the tools) and that memory is kept per user.
@SpringBootTest(properties = "fitbook.reminders.enabled=false")
class ChatAgentServiceTest {

    @Autowired SlotsService slotsService;
    @Autowired AppointmentsRepository appointmentsRepository;
    @Autowired AppointmentFacade appointmentFacade;
    @Autowired PendingActionStore pendingStore;

    private final List<Prompt> prompts = new ArrayList<>();
    private ChatAgentService service;

    @BeforeEach
    void setUp() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Shall I go ahead?"))));
        });
        service = new ChatAgentService(ChatClient.builder(model), slotsService, appointmentsRepository,
                appointmentFacade, pendingStore, new LlmRetry(1, 1));
    }

    private static List<String> textsOf(Prompt prompt, MessageType type) {
        return prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == type).map(Message::getText).toList();
    }

    @Test
    void returnsTheModelsReply() {
        assertThat(service.chat(1L, "Book me a slot")).isEqualTo("Shall I go ahead?");
    }

    @Test
    void sendsTheSafetyRulesToTheModelAsTheSystemPrompt() {
        service.chat(1L, "hello");

        String system = textsOf(prompts.get(0), MessageType.SYSTEM).get(0);
        assertThat(system).contains("proposeBooking").contains("confirmPendingAction")
                .contains("Never propose and confirm in the same turn");
        assertThat(system).contains(java.time.LocalDate.now().toString());   // today's date for "tomorrow"
    }

    @Test
    void remembersEarlierMessagesOfTheSameUser() {
        service.chat(1L, "Find me a Pilates slot");
        service.chat(1L, "Yes");

        assertThat(textsOf(prompts.get(1), MessageType.USER))
                .contains("Find me a Pilates slot", "Yes");
    }

    @Test
    void keepsEachUsersConversationSeparate() {
        service.chat(1L, "My secret request");
        service.chat(4L, "Hi from another customer");

        assertThat(textsOf(prompts.get(1), MessageType.USER))
                .containsExactly("Hi from another customer");
    }
}
