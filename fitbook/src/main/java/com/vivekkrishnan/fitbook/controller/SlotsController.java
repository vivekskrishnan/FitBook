package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotsController {

    private final SlotsService slotsService;

    public SlotsController(SlotsService slotsService) {
        this.slotsService = slotsService;
    }

    @GetMapping("/slots")
    public SlotsDTO slots() {
        return slotsService.getSlotsData();
    }
}
