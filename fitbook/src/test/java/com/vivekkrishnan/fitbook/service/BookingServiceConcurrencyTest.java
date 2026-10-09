package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the race condition the assignment calls out can't happen: two threads
// book the same slot at the same instant, exactly one must win.
@SpringBootTest
class BookingServiceConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AppointmentsRepository appointmentsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long slotId;

    @BeforeEach
    void seedOpenSlot() {
        LocalDateTime start = LocalDateTime.now().plusYears(1);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status) "
                            + "VALUES (1, 1, ?, ?, 'OPEN')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setObject(1, start);
            ps.setObject(2, start.plusHours(1));
            return ps;
        }, keyHolder);
        slotId = keyHolder.getKey().longValue();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM appointments WHERE slot_id = ?", slotId);
        jdbcTemplate.update("DELETE FROM availability_slots WHERE slot_id = ?", slotId);
    }

    @Test
    void onlyOneOfTwoSimultaneousBookingsSucceeds() throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(bookingAttempt(1L, readyLatch, startLatch, successCount, conflictCount));
        executor.submit(bookingAttempt(2L, readyLatch, startLatch, successCount, conflictCount));

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // release both threads at (as close to) the same instant

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).as("both booking attempts finished in time").isTrue();
        assertThat(successCount.get()).as("exactly one booking should succeed").isEqualTo(1);
        assertThat(conflictCount.get()).as("exactly one booking should be rejected").isEqualTo(1);
        assertThat(appointmentsRepository.countBySlot(slotId))
                .as("exactly one appointment row should exist for the slot")
                .isEqualTo(1);
    }

    private Runnable bookingAttempt(
            long customerId,
            CountDownLatch readyLatch,
            CountDownLatch startLatch,
            AtomicInteger successCount,
            AtomicInteger conflictCount
    ) {
        return () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            try {
                bookingService.book(slotId, customerId);
                successCount.incrementAndGet();
            } catch (SlotConflictException e) {
                conflictCount.incrementAndGet();
            }
        };
    }
}
