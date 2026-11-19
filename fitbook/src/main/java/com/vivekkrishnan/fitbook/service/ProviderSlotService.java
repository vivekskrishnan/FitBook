package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.exception.ValidationException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProviderSlotService {

    private final SlotsRepository slotsRepository;

    private final AppointmentsRepository appointmentsRepository;

    public ProviderSlotService(SlotsRepository slotsRepository, AppointmentsRepository appointmentsRepository) {
        this.slotsRepository = slotsRepository;
        this.appointmentsRepository = appointmentsRepository;
    }

    public List<SlotsDTO.Slot> listMySlots(Long userId) {
        SlotsRepository.TrainerInfo trainer = requireTrainer(userId);
        return slotsRepository.findByTrainerId(trainer.trainerId());
    }

    // service_id is not exposed as a choice in the create form - each trainer has a
    // single assigned service (trainers.service_id), so a new slot just inherits it.
    public void createSlot(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        SlotsRepository.TrainerInfo trainer = requireTrainer(userId);
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ValidationException("End time must be after start time");
        }
        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new ValidationException("Start time must be in the future");
        }
        if (slotsRepository.existsOverlap(trainer.trainerId(), startTime, endTime)) {
            throw new SlotConflictException("This overlaps an existing slot");
        }
        slotsRepository.insertSlot(trainer.trainerId(), trainer.serviceId(), startTime, endTime);
    }

    // Blocks removing a BOOKED slot: uq_appt_slot ties one appointment permanently
    // to that slot row, so deleting it would orphan a customer's appointment.
    public void deleteSlot(Long userId, Long slotId) {
        SlotsRepository.TrainerInfo trainer = requireTrainer(userId);
        SlotsDTO.Slot slot = slotsRepository.findById(slotId);
        if (slot == null) {
            throw new NotFoundException("Slot " + slotId + " does not exist");
        }
        if (!slot.trainerId().equals(trainer.trainerId())) {
            throw new ForbiddenException("Slot " + slotId + " does not belong to this provider");
        }
        if ("BOOKED".equals(slot.status())) {
            throw new SlotConflictException("Cannot remove slot " + slotId + " - it already has a booking");
        }
        if (appointmentsRepository.countBySlot(slotId) > 0) {
            throw new SlotConflictException("Cannot remove slot " + slotId + " - it has booking history");
        }
        slotsRepository.deleteSlot(slotId);
    }

    private SlotsRepository.TrainerInfo requireTrainer(Long userId) {
        SlotsRepository.TrainerInfo trainer = slotsRepository.findTrainerByUserId(userId);
        if (trainer == null) {
            throw new NotFoundException("No trainer profile for user " + userId);
        }
        return trainer;
    }
}
