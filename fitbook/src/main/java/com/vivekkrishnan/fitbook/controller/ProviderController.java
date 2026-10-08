package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.exception.ValidationException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.ProviderSlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

// All routes here are under /provider/** - SecurityConfig restricts that prefix to
// ROLE_TRAINER, so principal is always a provider by the time these run.
@Controller
public class ProviderController {

    private final ProviderSlotService providerSlotService;
    private final SlotsRepository slotsRepository;
    private final AppointmentsRepository appointmentsRepository;

    public ProviderController(
            ProviderSlotService providerSlotService,
            SlotsRepository slotsRepository,
            AppointmentsRepository appointmentsRepository
    ) {
        this.providerSlotService = providerSlotService;
        this.slotsRepository = slotsRepository;
        this.appointmentsRepository = appointmentsRepository;
    }

    @GetMapping("/provider/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("name", principal.getName());
        model.addAttribute("role", principal.getRole());

        SlotsRepository.TrainerInfo trainer = slotsRepository.findTrainerByUserId(principal.getUserId());
        model.addAttribute("slots", providerSlotService.listMySlots(principal.getUserId()));
        model.addAttribute("bookings", appointmentsRepository.findDetailedByTrainer(trainer.trainerId()));

        return "provider-dashboard";
    }

    @PostMapping("/provider/slots")
    public String createSlot(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            RedirectAttributes redirectAttributes
    ) {
        try {
            providerSlotService.createSlot(principal.getUserId(), startTime, endTime);
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/provider/dashboard";
    }

    @PostMapping("/provider/slots/{id}/delete")
    public String deleteSlot(
            @PathVariable("id") Long slotId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        try {
            providerSlotService.deleteSlot(principal.getUserId(), slotId);
        } catch (ForbiddenException | NotFoundException | SlotConflictException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/provider/dashboard";
    }
}
