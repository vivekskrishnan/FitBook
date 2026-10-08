package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.BookingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// All routes here are under /book/** - SecurityConfig restricts that prefix to
// ROLE_CUSTOMER, so principal is always a customer by the time these run.
@Controller
public class BookingController {

    private final BookingService bookingService;
    private final SlotsRepository slotsRepository;
    private final AppointmentsRepository appointmentsRepository;

    public BookingController(
            BookingService bookingService,
            SlotsRepository slotsRepository,
            AppointmentsRepository appointmentsRepository
    ) {
        this.bookingService = bookingService;
        this.slotsRepository = slotsRepository;
        this.appointmentsRepository = appointmentsRepository;
    }

    @GetMapping("/book/{slotId}")
    public String bookForm(@PathVariable Long slotId, Model model) {
        SlotsDTO.Slot slot = slotsRepository.findById(slotId);
        if (slot == null) {
            throw new NotFoundException("Slot " + slotId + " does not exist");
        }
        model.addAttribute("slot", slot);
        return "booking-form";
    }

    @PostMapping("/book/{slotId}")
    public String confirm(
            @PathVariable Long slotId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        try {
            AppointmentDTO appointment = bookingService.book(slotId, principal.getUserId());
            return "redirect:/book/confirmation/" + appointment.appointmentId();
        } catch (SlotConflictException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/slots";
        }
    }

    @GetMapping("/book/confirmation/{appointmentId}")
    public String confirmation(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model
    ) {
        AppointmentsRepository.AppointmentDetail appointment = appointmentsRepository.findDetailById(appointmentId);
        if (appointment == null) {
            throw new NotFoundException("Appointment " + appointmentId + " does not exist");
        }
        if (!appointment.customerId().equals(principal.getUserId())) {
            throw new ForbiddenException("Appointment " + appointmentId + " does not belong to this customer");
        }
        model.addAttribute("appointment", appointment);
        return "confirmation";
    }
}
