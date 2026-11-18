package com.vivekkrishnan.fitbook.api;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vivekkrishnan.fitbook.ai.agent1.ChatAgentService;
import com.vivekkrishnan.fitbook.ai.agent2.ReminderAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// JWT authentication and the REST API, against the real database. Covers login, token validation
// (tampered, expired, wrong key, wrong issuer), role rules, and that the API uses the same booking
// path (and double-booking guard) as the website. AI services are mocked.
@SpringBootTest(properties = {"fitbook.reminders.enabled=false"})
@AutoConfigureMockMvc
class JwtApiTest {

    private static final String KAI = "kai.smith@example.com";        // CUSTOMER, id 1
    private static final String NYA = "nya.smith@example.com";        // CUSTOMER, id 4
    private static final String LLOYD = "lloyd.garmadon@example.com"; // TRAINER, id 2

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean ChatAgentService chatAgentService;
    @MockitoBean ReminderAgent reminderAgent;

    private Long slotId;

    @BeforeEach
    void seedSlot() {
        LocalDateTime start = LocalDateTime.now().plusYears(4);
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
        jdbc.update("DELETE FROM appointments WHERE slot_id = ?", slotId);
        jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", slotId);
    }

    // ---------- helpers ----------

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = loginRequest(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String customerToken() throws Exception { return tokenFor(KAI, "customer1"); }
    private String otherCustomerToken() throws Exception { return tokenFor(NYA, "customer2"); }
    private String trainerToken() throws Exception { return tokenFor(LLOYD, "trainer1"); }

    private static String bearer(String token) { return "Bearer " + token; }

    private String tokenWith(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(issuedAt).expiresAt(expiresAt)
                .subject("1").claim("email", KAI).claim("name", "Kai Smith").claim("role", "CUSTOMER").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // ---------- login ----------

    @Test
    void loginReturnsASignedTokenAndTheUser() throws Exception {
        loginRequest(KAI, "customer1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.user.email").value(KAI))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        assertThat(customerToken().split("\\.")).hasSize(3);   // header.payload.signature
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGenericError() throws Exception {
        String wrongPassword = loginRequest(KAI, "nope").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = loginRequest("nobody@example.com", "whatever").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);   // cannot probe which emails exist
        assertThat(wrongPassword).contains("Invalid email or password");
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginNeedsNoCsrfToken() throws Exception {
        loginRequest(KAI, "customer1").andExpect(status().isOk());   // stateless API: no cookie, no CSRF
    }

    // ---------- token validation ----------

    @Test
    void protectedEndpointsRejectRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheUserFromTheToken() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value(KAI))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void aTamperedTokenIsRejected() throws Exception {
        String token = customerToken();
        String[] parts = token.split("\\.");
        // Change the payload (role -> TRAINER) but keep the old signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("CUSTOMER", "TRAINER").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(forged))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer("not.a.jwt"))).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = tokenWith(jwtEncoder, "fitbook", past, past.plus(Duration.ofMinutes(5)));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(expired))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("an-attackers-own-secret-key-32-bytes!!".getBytes(), "HmacSHA256")));
        String forged = tokenWith(attacker, "fitbook", Instant.now(), Instant.now().plus(Duration.ofHours(1)));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(forged))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() throws Exception {
        String wrongIssuer = tokenWith(jwtEncoder, "someone-else", Instant.now(), Instant.now().plus(Duration.ofHours(1)));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(wrongIssuer))).andExpect(status().isUnauthorized());
    }

    @Test
    void theBearerTokenIsNotAcceptedByTheSessionBasedWebPages() throws Exception {
        mvc.perform(get("/my-appointments").header("Authorization", bearer(customerToken())))
                .andExpect(status().is3xxRedirection());   // web pages still want the form login
    }

    // ---------- public browsing ----------

    @Test
    void browsingSlotsNeedsNoToken() throws Exception {
        mvc.perform(get("/api/slots")).andExpect(status().isOk()).andExpect(jsonPath("$.slots").isArray());
        mvc.perform(get("/api/slots/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trainers").isArray()).andExpect(jsonPath("$.services").isArray());
    }

    // ---------- roles ----------

    @Test
    void customerEndpointsAreForbiddenForTrainersAndViceVersa() throws Exception {
        mvc.perform(get("/api/appointments").header("Authorization", bearer(trainerToken()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/provider/slots").header("Authorization", bearer(customerToken()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/chat").header("Authorization", bearer(trainerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/reminders/run").header("Authorization", bearer(customerToken()))).andExpect(status().isForbidden());
    }

    // ---------- booking, cancelling, double-booking ----------

    private ResultActions book(String token, Long slot) throws Exception {
        return mvc.perform(post("/api/appointments").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slot + "}"));
    }

    @Test
    void aCustomerCanBookAndSeeTheAppointment() throws Exception {
        String token = customerToken();

        book(token, slotId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.customerId").value(1));

        mvc.perform(get("/api/appointments").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slotId == " + slotId + ")].status", org.hamcrest.Matchers.hasItem("BOOKED")));
    }

    @Test
    void theApiBlocksDoubleBooking() throws Exception {
        book(customerToken(), slotId).andExpect(status().isCreated());

        book(otherCustomerToken(), slotId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Integer.class, slotId))
                .isEqualTo(1);
    }

    @Test
    void theCustomerIdComesFromTheTokenNotTheRequest() throws Exception {
        mvc.perform(post("/api/appointments").header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slotId + ",\"customerId\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(1));   // Kai, not the forged 4
    }

    @Test
    void bookingValidatesInput() throws Exception {
        mvc.perform(post("/api/appointments").header("Authorization", bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        book(customerToken(), 999_999_999L).andExpect(status().isConflict());   // slot does not exist
    }

    @Test
    void aCustomerCanCancelOnlyTheirOwnAppointment() throws Exception {
        String body = book(customerToken(), slotId).andReturn().getResponse().getContentAsString();
        Integer appointmentId = JsonPath.read(body, "$.appointmentId");

        mvc.perform(post("/api/appointments/" + appointmentId + "/cancel").header("Authorization", bearer(otherCustomerToken())))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/appointments/" + appointmentId + "/cancel").header("Authorization", bearer(customerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/appointments/" + appointmentId + "/cancel").header("Authorization", bearer(customerToken())))
                .andExpect(status().isConflict());   // already cancelled
        mvc.perform(post("/api/appointments/999999999/cancel").header("Authorization", bearer(customerToken())))
                .andExpect(status().isNotFound());
    }

    // ---------- trainer endpoints ----------

    @Test
    void aTrainerManagesTheirOwnSlots() throws Exception {
        String token = trainerToken();
        LocalDateTime start = LocalDateTime.now().plusYears(5).withNano(0);

        mvc.perform(post("/api/provider/slots").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isCreated());

        Long created = jdbc.queryForObject(
                "SELECT slot_id FROM availability_slots WHERE start_time = ? AND trainer_id = 1", Long.class, start);
        try {
            mvc.perform(get("/api/provider/slots").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.slotId == " + created + ")]").isNotEmpty());
            mvc.perform(delete("/api/provider/slots/" + created).header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
            created = null;
        } finally {
            if (created != null) {
                jdbc.update("DELETE FROM availability_slots WHERE slot_id = ?", created);
            }
        }
    }

    @Test
    void aTrainerCannotCreateASlotThatEndsBeforeItStarts() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusYears(5);
        mvc.perform(post("/api/provider/slots").header("Authorization", bearer(trainerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.minusHours(1) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTrainerSeesBookingsOnTheirSlots() throws Exception {
        book(customerToken(), slotId).andExpect(status().isCreated());

        mvc.perform(get("/api/provider/bookings").header("Authorization", bearer(trainerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slotId == " + slotId + ")]").isNotEmpty());
    }

    // ---------- AI endpoints with a JWT ----------

    @Test
    void theAiChatActsAsTheUserInTheToken() throws Exception {
        when(chatAgentService.chat(1L, "book it")).thenReturn("Shall I go ahead?");

        mvc.perform(post("/api/ai/chat").header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"book it\",\"userId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Shall I go ahead?"));

        verify(chatAgentService).chat(1L, "book it");
    }

    @Test
    void aTrainerCanTriggerRemindersWithAToken() throws Exception {
        when(reminderAgent.run(Duration.ofHours(24))).thenReturn(new ReminderAgent.RunSummary(1, 1, 0, 1, 0, 0));

        mvc.perform(post("/api/ai/reminders/run").header("Authorization", bearer(trainerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
    }

    @Test
    void aiChatRequiresAToken() throws Exception {
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        verify(chatAgentService, org.mockito.Mockito.never()).chat(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }
}
