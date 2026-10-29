package com.vivekkrishnan.fitbook.gateway;

import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// Calls the notification service over HTTP with:
//  - timeouts (connect + read), so a slow service cannot hang a booking,
//  - bounded retries with growing backoff for I/O errors, timeouts and 5xx,
//  - a fallback: after the last attempt we log, give up and return false.
// Every attempt sends the same DTO, hence the same idempotencyKey, so the receiver
// dedupes if an earlier attempt actually got through but its response was lost.
@Component
public class HttpNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final RestClient restClient;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public HttpNotificationGateway(
            @Value("${notification.base-url}") String baseUrl,
            @Value("${notification.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${notification.read-timeout-ms}") long readTimeoutMs,
            @Value("${notification.max-attempts}") int maxAttempts,
            @Value("${notification.retry-backoff-ms}") long retryBackoffMs
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = retryBackoffMs;
    }

    @Override
    public boolean send(NotificationRequestDTO request) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri("/mock-notifications")
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Notification sent: type={} key={} attempt={}",
                        request.type(), request.idempotencyKey(), attempt);
                return true;
            } catch (HttpClientErrorException e) {
                // 4xx: the request itself is bad, retrying the same payload cannot help.
                log.warn("Notification rejected ({}), not retrying: key={}",
                        e.getStatusCode().value(), request.idempotencyKey());
                return false;
            } catch (Exception e) {
                // Timeout, connection refused, or 5xx from the service.
                if (attempt < maxAttempts) {
                    log.warn("Notification attempt {}/{} failed ({}): key={}, retrying",
                            attempt, maxAttempts, e.getClass().getSimpleName(), request.idempotencyKey());
                    if (!pause(retryBackoffMs << (attempt - 1))) {
                        break;
                    }
                } else {
                    log.warn("Notification attempt {}/{} failed ({}): key={}",
                            attempt, maxAttempts, e.getClass().getSimpleName(), request.idempotencyKey());
                }
            }
        }
        log.warn("Notification not delivered after {} attempts, booking unaffected: key={}",
                maxAttempts, request.idempotencyKey());
        return false;
    }

    // Returns false if interrupted, so the caller stops retrying.
    private boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
