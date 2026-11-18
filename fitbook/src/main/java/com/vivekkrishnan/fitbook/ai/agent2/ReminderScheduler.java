package com.vivekkrishnan.fitbook.ai.agent2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

// The "autonomous" part: Spring calls this on a timer with no user involved.
// Turn it off with REMINDERS_ENABLED=false.
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderAgent agent;
    private final boolean enabled;
    private final long windowHours;

    public ReminderScheduler(ReminderAgent agent,
                             @Value("${fitbook.reminders.enabled:true}") boolean enabled,
                             @Value("${fitbook.reminders.window-hours:24}") long windowHours) {
        this.agent = agent;
        this.enabled = enabled;
        this.windowHours = windowHours;
    }

    @Scheduled(fixedDelayString = "${fitbook.reminders.interval-ms:3600000}",
            initialDelayString = "${fitbook.reminders.initial-delay-ms:300000}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            agent.run(Duration.ofHours(windowHours));
        } catch (RuntimeException e) {
            log.error("Scheduled reminder run failed", e);   // never let the scheduler thread die
        }
    }
}
