package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.exception.ValidationException;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProviderSlotService {

    private final SlotsRepository slotsRepository;

    public ProviderSlotService(SlotsRepository slotsRepository) {
        this.slotsRepository = slotsRepository;
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
