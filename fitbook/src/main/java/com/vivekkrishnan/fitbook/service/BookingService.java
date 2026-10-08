package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
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

        slotsRepository.markBooked(slotId);
        return appointmentsRepository.insertBooked(slotId, customerId, slot.serviceId());
    }
}
