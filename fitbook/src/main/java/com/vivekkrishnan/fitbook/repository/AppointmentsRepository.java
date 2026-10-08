package com.vivekkrishnan.fitbook.repository;

import com.vivekkrishnan.fitbook.dto.AppointmentDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
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

    // Joined view for display (my-appointments list, booking confirmation) -
    // same join shape as SlotsRepository.BASE_QUERY, from the appointments side.
    public record AppointmentDetail(
            Long appointmentId,
            Long slotId,
            Long customerId,
            String trainerName,
            String serviceName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal price,
            String status
    ) {}

    private static final String DETAIL_QUERY =
            "SELECT ap.appointment_id, ap.slot_id, ap.customer_id, u.name AS trainer_name, " +
            "s.service_name, a.start_time, a.end_time, s.price, ap.status " +
            "FROM appointments ap " +
            "JOIN availability_slots a ON ap.slot_id = a.slot_id " +
            "JOIN trainers t ON a.trainer_id = t.trainer_id " +
            "JOIN users u ON t.user_id = u.user_id " +
            "JOIN services s ON ap.service_id = s.service_id ";

    public List<AppointmentDetail> findDetailedByCustomer(Long customerId) {
        String sql = DETAIL_QUERY + "WHERE ap.customer_id = ? ORDER BY a.start_time DESC";
        return jdbcTemplate.query(sql, this::mapDetailRow, customerId);
    }

    public AppointmentDetail findDetailById(Long appointmentId) {
        String sql = DETAIL_QUERY + "WHERE ap.appointment_id = ?";
        List<AppointmentDetail> results = jdbcTemplate.query(sql, this::mapDetailRow, appointmentId);
        return results.isEmpty() ? null : results.get(0);
    }

    // DETAIL_QUERY already joins through to `trainers` as `t`, so filtering on
    // t.trainer_id gives the provider dashboard's "my bookings" view.
    public List<AppointmentDetail> findDetailedByTrainer(Long trainerId) {
        String sql = DETAIL_QUERY + "WHERE t.trainer_id = ? ORDER BY a.start_time DESC";
        return jdbcTemplate.query(sql, this::mapDetailRow, trainerId);
    }

    private AppointmentDetail mapDetailRow(ResultSet rs, int rowNum) throws SQLException {
        return new AppointmentDetail(
                rs.getLong("appointment_id"),
                rs.getLong("slot_id"),
                rs.getLong("customer_id"),
                rs.getString("trainer_name"),
                rs.getString("service_name"),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getBigDecimal("price"),
                rs.getString("status")
        );
    }
}
