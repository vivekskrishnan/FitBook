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

    public record Page(
            List<Slot> slots,
            int page,
            int pageSize,
            int totalCount,
            int totalPages
    ) {}
}
