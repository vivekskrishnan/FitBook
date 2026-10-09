package com.vivekkrishnan.fitbook.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

// Centralizes the mapping from domain exceptions to HTTP status + a friendly,
// no-stack-trace error page. Most controllers already catch these to redirect
// with a flash message for a nicer UX on form posts (e.g. ProviderController,
// AppointmentsController) - this is the fallback for everything that doesn't,
// such as a bad GET /book/{id} or /book/confirmation/{id}.
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NotFoundException e, Model model) {
        return errorView(model, HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(ForbiddenException e, Model model) {
        return errorView(model, HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(SlotConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(SlotConflictException e, Model model) {
        return errorView(model, HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidation(ValidationException e, Model model) {
        return errorView(model, HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Only reached if an AccessDeniedException is thrown from inside a controller
    // method itself. The far more common case - a role mismatch on a route guarded
    // by authorizeHttpRequests - is thrown by the security filter chain below
    // DispatcherServlet, never reaches a @ControllerAdvice, and is handled by
    // Spring Security's AccessDeniedHandler, which forwards to Boot's /error
    // dispatch and picks up templates/error.html the same way.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException e, Model model) {
        return errorView(model, HttpStatus.FORBIDDEN, "You don't have permission to do that.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception e, Model model) {
        log.error("Unhandled exception", e);
        return errorView(model, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private String errorView(Model model, HttpStatus status, String message) {
        model.addAttribute("status", status.value());
        model.addAttribute("message", message);
        return "error";
    }
}
