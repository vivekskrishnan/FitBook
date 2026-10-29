package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.dto.NotificationRequestDTO;
import com.vivekkrishnan.fitbook.gateway.NotificationGateway;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
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

    public AppointmentFacade(
            BookingService bookingService,
            AppointmentsRepository appointmentsRepository,
            NotificationGateway notificationGateway
    ) {
        this.bookingService = bookingService;
        this.appointmentsRepository = appointmentsRepository;
        this.notificationGateway = notificationGateway;
    }

    public AppointmentDTO book(Long slotId, Long customerId) {
        AppointmentDTO appointment = bookingService.book(slotId, customerId);
        log.info("Booking created: appointmentId={} slotId={} customerId={}",
                appointment.appointmentId(), slotId, customerId);
        notifyCustomer(appointment, NotificationRequestDTO.TYPE_BOOKING_CONFIRMED, "BOOKED", "confirmed");
        return appointment;
    }

    public AppointmentDTO cancel(Long appointmentId, Long customerId) {
        AppointmentDTO appointment = bookingService.cancel(appointmentId, customerId);
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
            notificationGateway.send(new NotificationRequestDTO(
                    keyPrefix + "-" + appointment.appointmentId(), recipient, type, message));
        } catch (RuntimeException e) {
            log.warn("Notification skipped, could not be prepared: appointmentId={}",
                    appointment.appointmentId(), e);
        }
    }
}
