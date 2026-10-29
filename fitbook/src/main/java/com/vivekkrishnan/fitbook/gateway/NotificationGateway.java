package com.vivekkrishnan.fitbook.gateway;

import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;

// Client side of the distribution boundary to the notification service. One coarse
// method: a single call per business event, carrying the whole DTO. Callers depend on
// this interface, so the HTTP implementation can be swapped (test stub, or a real
// Slack/Discord/SMTP channel) without touching them.
public interface NotificationGateway {

    // Returns true if the notification service accepted the request, false if it could
    // not be delivered. Never throws: a notification failure must not fail a booking.
    boolean send(NotificationRequestDTO request);
}
