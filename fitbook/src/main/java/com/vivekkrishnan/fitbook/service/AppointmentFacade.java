package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.gateway.NotificationGateway;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

// Commit-then-notify. Deliberately NOT @Transactional: BookingService.book()/cancel()
// each run in their own transaction and have committed by the time they return here,
// so the remote notification call happens outside any database transaction. A slow or
// dead notification service therefore can never hold row locks or roll back a booking.
// If book()/cancel() throw, we never reach the notify step, so only successful
// operations are announced.
@Service
public class AppointmentFacade {

    private static final Logger log = LoggerFactory.getLogger(AppointmentFacade.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a");

    private final BookingService bookingService;
    private final AppointmentsRepository appointmentsRepository;
    private final NotificationGateway notificationGateway;
    private final MeterRegistry meterRegistry;
    private final Timer bookingLatency;

    public AppointmentFacade(
            BookingService bookingService,
            AppointmentsRepository appointmentsRepository,
            NotificationGateway notificationGateway,
            MeterRegistry meterRegistry
    ) {
        this.bookingService = bookingService;
        this.appointmentsRepository = appointmentsRepository;
        this.notificationGateway = notificationGateway;
        this.meterRegistry = meterRegistry;
        this.bookingLatency = Timer.builder("fitbook.booking.latency")
                .description("Time spent in BookingService.book(), excluding the notification")
                .register(meterRegistry);
    }

    public AppointmentDTO book(Long slotId, Long customerId) {
        AppointmentDTO appointment;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            appointment = bookingService.book(slotId, customerId);
        } catch (SlotConflictException e) {
            meterRegistry.counter("fitbook.bookings.failed", "reason", "conflict").increment();
            log.warn("Booking rejected, slot already taken: slotId={} customerId={}", slotId, customerId);
            throw e;
        } finally {
            sample.stop(bookingLatency);
        }
        meterRegistry.counter("fitbook.bookings.success").increment();
        log.info("Booking created: appointmentId={} slotId={} customerId={}",
                appointment.appointmentId(), slotId, customerId);
        notifyCustomer(appointment, NotificationRequestDTO.TYPE_BOOKING_CONFIRMED, "BOOKED", "confirmed");
        return appointment;
    }

    public AppointmentDTO cancel(Long appointmentId, Long customerId) {
        AppointmentDTO appointment;
        try {
            appointment = bookingService.cancel(appointmentId, customerId);
        } catch (ForbiddenException e) {
            log.warn("Access denied, appointment belongs to another customer: appointmentId={} customerId={}",
                    appointmentId, customerId);
            throw e;
        }
        log.info("Appointment cancelled: appointmentId={} customerId={}", appointmentId, customerId);
        notifyCustomer(appointment, NotificationRequestDTO.TYPE_BOOKING_CANCELLED, "CANCELLED", "cancelled");
        return appointment;
    }

    // The data change is already committed, so nothing here may fail the caller. The
    // gateway itself never throws; the catch covers building the message (lookups).
    // Idempotency key = event + appointment id, so a retry of the same event is
    // recognised by the receiver.
    private void notifyCustomer(AppointmentDTO appointment, String type, String keyPrefix, String verb) {
        try {
            String recipient = appointmentsRepository.findCustomerEmail(appointment.customerId());
            AppointmentsRepository.AppointmentDetail detail =
                    appointmentsRepository.findDetailById(appointment.appointmentId());
            if (recipient == null || detail == null) {
                log.warn("Notification skipped, details not found: appointmentId={}", appointment.appointmentId());
                return;
            }
            String message = "Your " + detail.serviceName() + " session with " + detail.trainerName()
                    + " on " + detail.startTime().format(WHEN) + " has been " + verb + ".";
            boolean sent = notificationGateway.send(new NotificationRequestDTO(
                    keyPrefix + "-" + appointment.appointmentId(), recipient, type, message));
            meterRegistry.counter(sent ? "fitbook.notifications.sent" : "fitbook.notifications.failed").increment();
        } catch (RuntimeException e) {
            meterRegistry.counter("fitbook.notifications.failed").increment();
            log.warn("Notification skipped, could not be prepared: appointmentId={}",
                    appointment.appointmentId(), e);
        }
    }
}
