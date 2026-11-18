package com.vivekkrishnan.fitbook.api;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.service.AppointmentFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Customer-only (see SecurityConfig). Booking and cancelling go through AppointmentFacade, the same
// path as the web pages and the AI agent, so ownership checks, the double-booking guard and
// notifications are identical. The customer id always comes from the token, never the request.
@RestController
@RequestMapping("/api/appointments")
public class ApiAppointmentsController {

    public record BookRequest(Long slotId) {}

    private final AppointmentFacade appointmentFacade;
    private final AppointmentsRepository appointmentsRepository;

    public ApiAppointmentsController(AppointmentFacade appointmentFacade, AppointmentsRepository appointmentsRepository) {
        this.appointmentFacade = appointmentFacade;
        this.appointmentsRepository = appointmentsRepository;
    }

    @GetMapping
    public List<AppointmentsRepository.AppointmentDetail> mine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return appointmentsRepository.findDetailedByCustomer(principal.getUserId());
    }

    @PostMapping
    public ResponseEntity<?> book(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody BookRequest request) {
        if (request == null || request.slotId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "slotId is required."));
        }
        AppointmentDTO booked = appointmentFacade.book(request.slotId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentsRepository.findDetailById(booked.appointmentId()));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentsRepository.AppointmentDetail cancel(@AuthenticationPrincipal AppUserPrincipal principal,
                                                           @PathVariable("id") Long appointmentId) {
        appointmentFacade.cancel(appointmentId, principal.getUserId());
        return appointmentsRepository.findDetailById(appointmentId);
    }
}
