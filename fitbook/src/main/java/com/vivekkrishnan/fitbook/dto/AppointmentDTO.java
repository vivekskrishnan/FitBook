package com.vivekkrishnan.fitbook.dto;

import java.time.LocalDateTime;

public record AppointmentDTO(
        Long appointmentId,
        Long slotId,
        Long customerId,
        Long serviceId,
        String status,
        LocalDateTime createdAt
) {}
