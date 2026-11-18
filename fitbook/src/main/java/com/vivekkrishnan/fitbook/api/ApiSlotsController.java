package com.vivekkrishnan.fitbook.api;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Public browsing, same data as the /slots page.
@RestController
@RequestMapping("/api/slots")
public class ApiSlotsController {

    private final SlotsService slotsService;

    public ApiSlotsController(SlotsService slotsService) {
        this.slotsService = slotsService;
    }

    @GetMapping
    public SlotsDTO.Page slots(
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page) {
        return slotsService.getAvailableSlots(trainerId, serviceId, date, page);
    }

    // Filter dropdown values.
    @GetMapping("/options")
    public Map<String, List<SlotsRepository.Option>> options() {
        return Map.of("trainers", slotsService.getTrainerOptions(), "services", slotsService.getServiceOptions());
    }
}
