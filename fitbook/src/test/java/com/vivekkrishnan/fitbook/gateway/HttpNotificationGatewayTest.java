package com.vivekkrishnan.fitbook.gateway;

import com.sun.net.httpserver.HttpServer;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit tests of the real HTTP gateway against a tiny in-process server, so retry,
// no-retry-on-4xx and timeout behaviour are proven without Spring or MySQL.
class HttpNotificationGatewayTest {

    private static final NotificationRequestDTO REQUEST = new NotificationRequestDTO(
            "BOOKED-42", "kai@example.com", NotificationRequestDTO.TYPE_BOOKING_CONFIRMED, "hello");

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void createServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpNotificationGateway gateway(int maxAttempts, long readTimeoutMs) {
        // Several threads so a slow handler does not block the next retry's request.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new HttpNotificationGateway(baseUrl, 500, readTimeoutMs, maxAttempts, 10);
    }

    // Responds with each status in turn (the last one repeats) and records every body.
    private void respondWith(int... statuses) {
        server.createContext("/mock-notifications", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    @Test
    void retriesAfterServerErrorsAndSendsSameKeyEachTime() {
        respondWith(503, 503, 200);

        boolean sent = gateway(3, 1000).send(REQUEST);

        assertThat(sent).isTrue();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(bodies).hasSize(3).allSatisfy(body -> assertThat(body).contains("BOOKED-42"));
        assertThat(bodies).containsOnly(bodies.get(0));
    }

    @Test
    void givesUpAfterMaxAttemptsAndReturnsFalse() {
        respondWith(503);

        boolean sent = gateway(3, 1000).send(REQUEST);

        assertThat(sent).isFalse();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryOnClientError() {
        respondWith(400);

        boolean sent = gateway(3, 1000).send(REQUEST);

        assertThat(sent).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void readTimeoutFiresWhenServerIsSlow() {
        server.createContext("/mock-notifications", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        long start = System.nanoTime();
        boolean sent = gateway(2, 300).send(REQUEST);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(sent).isFalse();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(elapsedMs).isLessThan(1500);
    }
}
