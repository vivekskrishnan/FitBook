package com.vivekkrishnan.fitbook.ai.agent1;

import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Agent 1: customer-only (see SecurityConfig). The user id comes from the login session,
// never from the request body, so the agent can only act as the logged-in customer.
@RestController
@RequestMapping("/ai")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record ChatRequest(String message) {}

    private final ChatAgentService chatAgentService;

    public ChatController(ChatAgentService chatAgentService) {
        this.chatAgentService = chatAgentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide a message."));
        }
        try {
            return ResponseEntity.ok(Map.of("reply", chatAgentService.chat(principal.getUserId(), request.message())));
        } catch (RuntimeException e) {
            log.error("AI chat failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "The AI service is unavailable right now. Please try again."));
        }
    }
}
