package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.BookingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class AppointmentsController {

    private final AppointmentsRepository appointmentsRepository;
    private final BookingService bookingService;

    public AppointmentsController(AppointmentsRepository appointmentsRepository, BookingService bookingService) {
        this.appointmentsRepository = appointmentsRepository;
        this.bookingService = bookingService;
    }

    @GetMapping("/my-appointments")
    public String myAppointments(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("name", principal.getName());
        model.addAttribute("role", principal.getRole());

        List<AppointmentsRepository.AppointmentDetail> appointments =
                appointmentsRepository.findDetailedByCustomer(principal.getUserId());
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("now", now);

        model.addAttribute("upcoming", appointments.stream()
                .filter(a -> "BOOKED".equals(a.status()) && a.endTime().isAfter(now))
                .toList());
        // History: cancelled appointments, and BOOKED ones whose slot has ended -
        // the template displays those as COMPLETED, computed at read time rather
        // than stored (see plan section 2).
        model.addAttribute("history", appointments.stream()
                .filter(a -> "CANCELLED".equals(a.status()) || !a.endTime().isAfter(now))
                .toList());

        return "my-appointments";
    }

    @PostMapping("/my-appointments/{id}/cancel")
    public String cancel(
            @PathVariable("id") Long appointmentId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        try {
            bookingService.cancel(appointmentId, principal.getUserId());
        } catch (ForbiddenException | NotFoundException | SlotConflictException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/my-appointments";
    }
}
