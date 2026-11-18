package com.vivekkrishnan.fitbook.ai.agent2;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

// Manual trigger for the demo (trainer-only, see SecurityConfig): runs the same job the
// scheduler runs, so you don't have to wait for the timer.
@RestController
@RequestMapping("/ai/reminders")
public class ReminderController {

    private final ReminderAgent agent;

    public ReminderController(ReminderAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestParam(defaultValue = "24") long hours) {
        if (hours < 1 || hours > 24 * 30) {
            return ResponseEntity.badRequest().body(Map.of("error", "hours must be between 1 and 720"));
        }
        return ResponseEntity.ok(agent.run(Duration.ofHours(hours)));
    }
}
