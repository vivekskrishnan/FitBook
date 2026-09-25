package com.vivekkrishnan.fitbook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class HomeDTO {

    public record User(Long userId, String name, String email, String role, LocalDateTime createdAt) {}

    public record Trainer(Long trainerId, Long userId, Long serviceId, String serviceName, LocalDateTime createdAt) {}

    public record Service(Long serviceId, String serviceName, String description, Integer durationMinutes, BigDecimal price) {}

    private final List<User> users;
    private final List<Trainer> trainers;
    private final List<Service> services;

    public HomeDTO(List<User> users, List<Trainer> trainers, List<Service> services) {
        this.users = users;
        this.trainers = trainers;
        this.services = services;
    }

    public List<User> getUsers() {
        return users;
    }

    public List<Trainer> getTrainers() {
        return trainers;
    }

    public List<Service> getServices() {
        return services;
    }
}
