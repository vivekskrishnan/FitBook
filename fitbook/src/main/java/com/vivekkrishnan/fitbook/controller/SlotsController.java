package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// Public browse page (/slots is permitAll). Booking (/book/**) is customer-only,
// so this page only offers a "Book" link when the viewer is a logged-in customer.
@Controller
public class SlotsController {

    private final SlotsService slotsService;

    public SlotsController(SlotsService slotsService) {
        this.slotsService = slotsService;
    }

    @GetMapping("/slots")
    public String slots(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        SlotsDTO.Page slotsPage = slotsService.getAvailableSlots(trainerId, serviceId, date, page);

        model.addAttribute("slotsPage", slotsPage);
        model.addAttribute("trainers", slotsService.getTrainerOptions());
        model.addAttribute("services", slotsService.getServiceOptions());
        model.addAttribute("selectedTrainerId", trainerId);
        model.addAttribute("selectedServiceId", serviceId);
        model.addAttribute("selectedDate", date);
        model.addAttribute("canBook", principal != null && "CUSTOMER".equals(principal.getRole()));
        return "slots";
    }
}
