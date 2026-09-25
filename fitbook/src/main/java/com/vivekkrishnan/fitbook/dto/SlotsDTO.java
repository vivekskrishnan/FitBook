package com.vivekkrishnan.fitbook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SlotsDTO {

    public record Slot(
            Long slotId,
            Long trainerId,
            String trainerName,
            Long serviceId,
            String serviceName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal price,
            String status
    ) {}

    private final List<Slot> allSlots;
    private final List<Slot> availableSlots;

    public SlotsDTO(List<Slot> allSlots, List<Slot> availableSlots) {
        this.allSlots = allSlots;
        this.availableSlots = availableSlots;
    }

    public List<Slot> getAllSlots() {
        return allSlots;
    }

    public List<Slot> getAvailableSlots() {
        return availableSlots;
    }
}
