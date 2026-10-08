package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Stub for the auth checkpoint. Phase 2 fills this in with real appointment data.
@Controller
public class AppointmentsController {

    @GetMapping("/my-appointments")
    public String myAppointments(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("name", principal.getName());
        model.addAttribute("role", principal.getRole());
        return "my-appointments";
    }
}
