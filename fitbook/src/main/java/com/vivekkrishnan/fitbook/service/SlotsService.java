package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import org.springframework.stereotype.Service;

@Service
public class SlotsService {

    private final SlotsRepository slotsRepository;

    public SlotsService(SlotsRepository slotsRepository) {
        this.slotsRepository = slotsRepository;
    }

    public SlotsDTO getSlotsData() {
        return new SlotsDTO(
                slotsRepository.findAllSlots(),
                slotsRepository.findAvailableSlots()
        );
    }
}
