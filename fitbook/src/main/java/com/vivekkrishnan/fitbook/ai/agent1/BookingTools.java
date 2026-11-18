package com.vivekkrishnan.fitbook.ai.agent1;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import com.vivekkrishnan.fitbook.exception.ForbiddenException;
import com.vivekkrishnan.fitbook.exception.NotFoundException;
import com.vivekkrishnan.fitbook.exception.SlotConflictException;
import com.vivekkrishnan.fitbook.repository.AppointmentsRepository;
import com.vivekkrishnan.fitbook.service.AppointmentFacade;
import com.vivekkrishnan.fitbook.service.SlotsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

// The functions the chat model is allowed to call. One instance is created PER chat request,
// bound to the logged-in user's id and a unique turn id. The model never supplies a user id:
// it cannot book or cancel for anyone else. Writes go through AppointmentFacade ->
// BookingService (transactional, double-booking guard), the same path as the web pages.
public class BookingTools {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, yyyy h:mm a");
    private static final int MAX_SLOTS = 10;

    private final Long userId;
    private final String turnId;
    private final SlotsService slotsService;
    private final AppointmentsRepository appointmentsRepository;
    private final AppointmentFacade appointmentFacade;
    private final PendingActionStore pendingStore;

    public BookingTools(Long userId, String turnId, SlotsService slotsService,
                        AppointmentsRepository appointmentsRepository, AppointmentFacade appointmentFacade,
                        PendingActionStore pendingStore) {
        this.userId = userId;
        this.turnId = turnId;
        this.slotsService = slotsService;
        this.appointmentsRepository = appointmentsRepository;
        this.appointmentFacade = appointmentFacade;
        this.pendingStore = pendingStore;
    }

    @Tool(description = "List the trainers and services with their ids. Call this to turn a trainer or service name into an id before checking availability.")
    public String listTrainersAndServices() {
        String trainers = slotsService.getTrainerOptions().stream()
                .map(o -> o.id() + " = " + o.name()).collect(Collectors.joining("; "));
        String services = slotsService.getServiceOptions().stream()
                .map(o -> o.id() + " = " + o.name()).collect(Collectors.joining("; "));
        return "Trainers: " + trainers + ". Services: " + services + ".";
    }

    @Tool(description = "Find OPEN appointment slots, earliest first (max 10). All filters are optional. Use this before proposing a booking.")
    public String checkAvailability(
            @ToolParam(required = false, description = "Trainer id, from listTrainersAndServices") Long trainerId,
            @ToolParam(required = false, description = "Service id, from listTrainersAndServices") Long serviceId,
            @ToolParam(required = false, description = "Only this day, formatted yyyy-MM-dd") String date) {
        LocalDate day = null;
        if (date != null && !date.isBlank()) {
            try {
                day = LocalDate.parse(date.strip());
            } catch (DateTimeParseException e) {
                return "Invalid date '" + date + "'. Use yyyy-MM-dd.";
            }
        }
        List<SlotsDTO.Slot> slots = slotsService.getAvailableSlots(trainerId, serviceId, day, 0).slots();
        if (slots.isEmpty()) {
            return "No open slots match those filters.";
        }
        return slots.stream().limit(MAX_SLOTS)
                .map(s -> "slotId=" + s.slotId() + ": " + s.serviceName() + " with " + s.trainerName()
                        + ", " + s.startTime().format(WHEN) + ", $" + s.price())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "List the current user's appointments (id, service, trainer, time, status).")
    public String listMyAppointments() {
        List<AppointmentsRepository.AppointmentDetail> list = appointmentsRepository.findDetailedByCustomer(userId);
        if (list.isEmpty()) {
            return "You have no appointments.";
        }
        return list.stream()
                .map(a -> "appointmentId=" + a.appointmentId() + ": " + a.serviceName() + " with " + a.trainerName()
                        + ", " + a.startTime().format(WHEN) + ", status " + a.status())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "STEP 1 of booking. Does NOT book. Records a proposal for an open slot and returns a summary to show the user. The user must then say yes in a later message.")
    public String proposeBooking(@ToolParam(description = "Id of the slot to book") Long slotId) {
        SlotsDTO.Slot slot = slotsService.getSlot(slotId);
        if (slot == null) {
            return "Slot " + slotId + " does not exist.";
        }
        if (!"OPEN".equals(slot.status())) {
            return "Slot " + slotId + " is no longer available. Offer the user other open slots.";
        }
        String summary = slot.serviceName() + " with " + slot.trainerName() + " on "
                + slot.startTime().format(WHEN) + " for $" + slot.price();
        pendingStore.put(userId, new PendingActionStore.Pending(
                PendingActionStore.Kind.BOOK, slotId, summary, turnId, Instant.now()));
        return "Proposed booking: " + summary + ". NOTHING IS BOOKED YET. Show this to the user and ask them to confirm.";
    }

    @Tool(description = "STEP 1 of cancelling. Does NOT cancel. Records a proposal to cancel one of the user's own appointments and returns a summary. The user must then say yes in a later message.")
    public String proposeCancellation(@ToolParam(description = "Id of the appointment to cancel") Long appointmentId) {
        AppointmentsRepository.AppointmentDetail detail = appointmentsRepository.findDetailById(appointmentId);
        if (detail == null || !detail.customerId().equals(userId)) {
            return "You have no appointment with id " + appointmentId + ".";
        }
        if (!"BOOKED".equals(detail.status())) {
            return "Appointment " + appointmentId + " is already " + detail.status() + ".";
        }
        String summary = detail.serviceName() + " with " + detail.trainerName() + " on "
                + detail.startTime().format(WHEN);
        pendingStore.put(userId, new PendingActionStore.Pending(
                PendingActionStore.Kind.CANCEL, appointmentId, summary, turnId, Instant.now()));
        return "Proposed cancellation: " + summary + ". NOTHING IS CANCELLED YET. Show this to the user and ask them to confirm.";
    }

    @Tool(description = "STEP 2. Carries out the pending booking or cancellation. Call ONLY after the user has explicitly said yes in their latest message.")
    public String confirmPendingAction() {
        PendingActionStore.Pending pending = pendingStore.get(userId);
        if (pending == null) {
            return "There is nothing pending to confirm.";
        }
        if (pending.turnId().equals(turnId)) {
            // Guard: proposed in this same request, so the user has not had a chance to agree.
            return "Refused: the user has not confirmed yet. Show the summary and wait for their reply.";
        }
        try {
            if (pending.kind() == PendingActionStore.Kind.BOOK) {
                appointmentFacade.book(pending.targetId(), userId);
                pendingStore.clear(userId);
                return "Booked: " + pending.summary() + ". A confirmation message was sent.";
            }
            appointmentFacade.cancel(pending.targetId(), userId);
            pendingStore.clear(userId);
            return "Cancelled: " + pending.summary() + ". A confirmation message was sent.";
        } catch (SlotConflictException e) {
            pendingStore.clear(userId);
            return "That could not be done: " + e.getMessage() + ". Offer the user alternative open slots.";
        } catch (ForbiddenException | NotFoundException e) {
            pendingStore.clear(userId);
            return "That could not be done: " + e.getMessage();
        }
    }

    @Tool(description = "Discard the pending booking or cancellation, e.g. when the user says no or changes their mind.")
    public String cancelPendingAction() {
        pendingStore.clear(userId);
        return "Pending action discarded.";
    }
}
