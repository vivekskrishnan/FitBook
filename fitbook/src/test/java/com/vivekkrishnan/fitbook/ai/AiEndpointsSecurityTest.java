package com.vivekkrishnan.fitbook.ai;

import com.vivekkrishnan.fitbook.ai.agent1.ChatAgentService;
import com.vivekkrishnan.fitbook.ai.agent2.ReminderAgent;
import com.vivekkrishnan.fitbook.ai.rag.KnowledgeService;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP + security rules for the three AI endpoints. The AI services are mocked, so this checks
// who may call what and how errors look, without calling Gemini.
@SpringBootTest(properties = "fitbook.reminders.enabled=false")
@AutoConfigureMockMvc
class AiEndpointsSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean KnowledgeService knowledgeService;
    @MockitoBean ChatAgentService chatAgentService;
    @MockitoBean ReminderAgent reminderAgent;

    private static AppUserPrincipal customer() {
        return new AppUserPrincipal(1L, "Kai Smith", "kai.smith@example.com", "x", "CUSTOMER");
    }

    private static AppUserPrincipal trainer() {
        return new AppUserPrincipal(2L, "Lloyd Garmadon", "lloyd.garmadon@example.com", "x", "TRAINER");
    }

    // ---- /ai/ask (RAG): any logged-in user ----

    @Test
    void askRequiresLogin() throws Exception {
        mvc.perform(get("/ai/ask").param("q", "hours"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void askReturnsAnswerAndSourcesToAnyLoggedInUser() throws Exception {
        when(knowledgeService.ask("hours")).thenReturn(
                new KnowledgeService.Answer("Open 6 to 9.", List.of("Studio hours")));

        mvc.perform(get("/ai/ask").param("q", "hours").with(user(trainer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Open 6 to 9."))
                .andExpect(jsonPath("$.sources[0]").value("Studio hours"));
    }

    @Test
    void askRejectsABlankQuestion() throws Exception {
        mvc.perform(get("/ai/ask").param("q", "  ").with(user(customer())))
                .andExpect(status().isBadRequest());
        verify(knowledgeService, never()).ask(anyString());
    }

    @Test
    void askHidesInternalsWhenTheAiFails() throws Exception {
        when(knowledgeService.ask(anyString())).thenThrow(new RuntimeException("429 secret internal detail"));

        mvc.perform(get("/ai/ask").param("q", "hours").with(user(customer())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The AI service is unavailable right now. Please try again."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    // ---- /ai/chat (Agent 1): customers only ----

    @Test
    void chatRequiresLogin() throws Exception {
        mvc.perform(post("/ai/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void chatIsForbiddenForTrainers() throws Exception {
        mvc.perform(post("/ai/chat").with(user(trainer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
        verify(chatAgentService, never()).chat(anyLong(), anyString());
    }

    @Test
    void chatRequiresACsrfToken() throws Exception {
        mvc.perform(post("/ai/chat").with(user(customer())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatUsesTheLoggedInUsersIdAndNothingFromTheRequestBody() throws Exception {
        when(chatAgentService.chat(eq(1L), anyString())).thenReturn("Shall I go ahead?");

        // The body tries to smuggle in another user id; it must be ignored.
        mvc.perform(post("/ai/chat").with(user(customer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"book it\",\"userId\":4,\"customerId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Shall I go ahead?"));

        verify(chatAgentService).chat(1L, "book it");
    }

    @Test
    void chatRejectsABlankMessage() throws Exception {
        mvc.perform(post("/ai/chat").with(user(customer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verify(chatAgentService, never()).chat(anyLong(), anyString());
    }

    @Test
    void chatHidesInternalsWhenTheAiFails() throws Exception {
        when(chatAgentService.chat(anyLong(), anyString())).thenThrow(new RuntimeException("quota secret"));

        mvc.perform(post("/ai/chat").with(user(customer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void chatPageIsForCustomersOnly() throws Exception {
        mvc.perform(get("/ai/chat").with(user(customer()))).andExpect(status().isOk());
        mvc.perform(get("/ai/chat").with(user(trainer()))).andExpect(status().isForbidden());
    }

    // ---- /ai/reminders/run (Agent 2): trainers only ----

    @Test
    void reminderTriggerIsForbiddenForCustomers() throws Exception {
        mvc.perform(post("/ai/reminders/run").with(user(customer())).with(csrf()))
                .andExpect(status().isForbidden());
        verify(reminderAgent, never()).run(any(Duration.class));
    }

    @Test
    void reminderTriggerRequiresLogin() throws Exception {
        mvc.perform(post("/ai/reminders/run").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void trainerCanTriggerRemindersAndGetsASummary() throws Exception {
        when(reminderAgent.run(Duration.ofHours(24)))
                .thenReturn(new ReminderAgent.RunSummary(2, 2, 0, 2, 0, 0));

        mvc.perform(post("/ai/reminders/run").with(user(trainer())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(2))
                .andExpect(jsonPath("$.sent").value(2))
                .andExpect(jsonPath("$.llmWritten").value(2));
    }

    @Test
    void reminderWindowIsValidated() throws Exception {
        mvc.perform(post("/ai/reminders/run").param("hours", "0").with(user(trainer())).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ai/reminders/run").param("hours", "100000").with(user(trainer())).with(csrf()))
                .andExpect(status().isBadRequest());
        verify(reminderAgent, never()).run(any(Duration.class));
    }
}
