package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SlotsService {

    private static final int PAGE_SIZE = 10;

    private final SlotsRepository slotsRepository;

    public SlotsService(SlotsRepository slotsRepository) {
        this.slotsRepository = slotsRepository;
    }

    public SlotsDTO.Page getAvailableSlots(Long trainerId, Long serviceId, LocalDate date, int page) {
        int totalCount = slotsRepository.countAvailableSlots(trainerId, serviceId, date);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(page, 0), totalPages - 1);

        List<SlotsDTO.Slot> slots = slotsRepository.findAvailableSlots(
                trainerId, serviceId, date, PAGE_SIZE, safePage * PAGE_SIZE);

        return new SlotsDTO.Page(slots, safePage, PAGE_SIZE, totalCount, totalPages);
    }

    public List<SlotsRepository.Option> getTrainerOptions() {
        return slotsRepository.findTrainerOptions();
    }

    public List<SlotsRepository.Option> getServiceOptions() {
        return slotsRepository.findServiceOptions();
    }
}
