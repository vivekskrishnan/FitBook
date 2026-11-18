package com.vivekkrishnan.fitbook.api;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.repository.SlotsRepository;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.ProviderSlotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

// Trainer-only (see SecurityConfig): the trainer's own slots and bookings. Every call is scoped to
// the trainer in the token; ProviderSlotService also checks slot ownership.
@RestController
@RequestMapping("/api/provider")
public class ApiProviderController {

    public record CreateSlotRequest(LocalDateTime startTime, LocalDateTime endTime) {}

    private final ProviderSlotService providerSlotService;
    private final SlotsRepository slotsRepository;
    private final AppointmentsRepository appointmentsRepository;

    public ApiProviderController(ProviderSlotService providerSlotService, SlotsRepository slotsRepository,
                                 AppointmentsRepository appointmentsRepository) {
        this.providerSlotService = providerSlotService;
        this.slotsRepository = slotsRepository;
        this.appointmentsRepository = appointmentsRepository;
    }

    @GetMapping("/slots")
    public List<SlotsDTO.Slot> mySlots(@AuthenticationPrincipal AppUserPrincipal principal) {
        return providerSlotService.listMySlots(principal.getUserId());
    }

    @PostMapping("/slots")
    public ResponseEntity<List<SlotsDTO.Slot>> createSlot(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestBody CreateSlotRequest request) {
        providerSlotService.createSlot(principal.getUserId(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(providerSlotService.listMySlots(principal.getUserId()));
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> deleteSlot(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable("id") Long slotId) {
        providerSlotService.deleteSlot(principal.getUserId(), slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookings")
    public List<AppointmentsRepository.AppointmentDetail> myBookings(@AuthenticationPrincipal AppUserPrincipal principal) {
        SlotsRepository.TrainerInfo trainer = slotsRepository.findTrainerByUserId(principal.getUserId());
        return appointmentsRepository.findDetailedByTrainer(trainer.trainerId());
    }
}
