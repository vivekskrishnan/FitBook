package com.vivekkrishnan.fitbook.repository;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AppointmentsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppointmentsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppointmentDTO insertBooked(Long slotId, Long customerId, Long serviceId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO appointments (slot_id, customer_id, service_id, status) VALUES (?, ?, ?, 'BOOKED')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, slotId);
            ps.setLong(2, customerId);
            ps.setLong(3, serviceId);
            return ps;
        }, keyHolder);

        return findById(keyHolder.getKey().longValue());
    }

    // Returns null rather than throwing when the id doesn't exist, so callers
    // (e.g. BookingService.cancel) can turn a miss into a NotFoundException.
    public AppointmentDTO findById(Long appointmentId) {
        String sql = "SELECT appointment_id, slot_id, customer_id, service_id, status, created_at " +
                "FROM appointments WHERE appointment_id = ?";
        List<AppointmentDTO> results = jdbcTemplate.query(sql, (rs, rowNum) -> new AppointmentDTO(
                rs.getLong("appointment_id"),
                rs.getLong("slot_id"),
                rs.getLong("customer_id"),
                rs.getLong("service_id"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), appointmentId);
        return results.isEmpty() ? null : results.get(0);
    }

    public void markCancelled(Long appointmentId) {
        jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED' WHERE appointment_id = ?", appointmentId);
    }

    public long countBySlot(Long slotId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slotId);
        return count == null ? 0 : count;
    }
}
