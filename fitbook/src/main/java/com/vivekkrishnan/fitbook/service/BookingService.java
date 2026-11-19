package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final SlotsRepository slotsRepository;
    private final AppointmentsRepository appointmentsRepository;

    public BookingService(SlotsRepository slotsRepository, AppointmentsRepository appointmentsRepository) {
        this.slotsRepository = slotsRepository;
        this.appointmentsRepository = appointmentsRepository;
    }

    // READ COMMITTED + SELECT ... FOR UPDATE: the row lock, not the isolation level,
    // is what serializes concurrent bookings of the same slot. A second transaction
    // blocks on lockForUpdate() until this one commits, then correctly sees BOOKED
    // and is rejected - no optimistic retry loop needed.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDTO book(Long slotId, Long customerId) {
        SlotsRepository.SlotLock slot = slotsRepository.lockForUpdate(slotId);
        if (slot == null) {
            throw new SlotConflictException("Slot " + slotId + " does not exist");
        }
        if (!"OPEN".equals(slot.status())) {
            throw new SlotConflictException("Slot " + slotId + " is no longer available");
        }

        if (slot.startTime() != null && !slot.startTime().isAfter(java.time.LocalDateTime.now())) {
            throw new SlotConflictException("Slot " + slotId + " is in the past");
        }

        slotsRepository.markBooked(slotId);
        return appointmentsRepository.insertBooked(slotId, customerId, slot.serviceId());
    }

    // Owner-only: a customer may only cancel their own appointment, regardless of
    // role-level route protection (that's enforced separately at the controller).
    // Cancelling keeps the appointment row as history (status CANCELLED) and reopens the slot.
    @Transactional
    public AppointmentDTO cancel(Long appointmentId, Long customerId) {
        AppointmentDTO appointment = appointmentsRepository.findById(appointmentId);
        if (appointment == null) {
            throw new NotFoundException("Appointment " + appointmentId + " does not exist");
        }
        if (!appointment.customerId().equals(customerId)) {
            throw new ForbiddenException("Appointment " + appointmentId + " does not belong to this customer");
        }
        if ("CANCELLED".equals(appointment.status())) {
            throw new SlotConflictException("Appointment " + appointmentId + " is already cancelled");
        }

        slotsRepository.lockForUpdate(appointment.slotId());
        appointmentsRepository.markCancelled(appointmentId);
        slotsRepository.markOpen(appointment.slotId());
        return appointmentsRepository.findById(appointmentId);
    }
}
