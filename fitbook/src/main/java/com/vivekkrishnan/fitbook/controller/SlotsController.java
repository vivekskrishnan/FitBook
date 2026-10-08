package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Public browse page (/slots is permitAll). Booking (/book/**) is customer-only,
// so this page only offers a "Book" link when the viewer is a logged-in customer.
@Controller
public class SlotsController {

    private final SlotsService slotsService;

    public SlotsController(SlotsService slotsService) {
        this.slotsService = slotsService;
    }

    @GetMapping("/slots")
    public String slots(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("slots", slotsService.getSlotsData().getAvailableSlots());
        model.addAttribute("canBook", principal != null && "CUSTOMER".equals(principal.getRole()));
        return "slots";
    }
}
