package com.vivekkrishnan.fitbook.repository;

import com.vivekkrishnan.fitbook.dto.SlotsDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SlotsRepository {

    private static final String BASE_QUERY =
            "SELECT a.slot_id, t.trainer_id, u.name AS trainer_name, " +
            "s.service_id, s.service_name, a.start_time, a.end_time, s.price, a.status " +
            "FROM availability_slots a " +
            "JOIN trainers t ON a.trainer_id = t.trainer_id " +
            "JOIN users u ON t.user_id = u.user_id " +
            "JOIN services s ON a.service_id = s.service_id ";

    private final JdbcTemplate jdbcTemplate;

    public SlotsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotsDTO.Slot> findAllSlots() {
        String sql = BASE_QUERY + "ORDER BY a.start_time";
        return jdbcTemplate.query(sql, this::mapRow);
    }

    public List<SlotsDTO.Slot> findAvailableSlots() {
        String sql = BASE_QUERY + "WHERE a.status = 'OPEN' ORDER BY a.start_time";
        return jdbcTemplate.query(sql, this::mapRow);
    }

    private SlotsDTO.Slot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SlotsDTO.Slot(
                rs.getLong("slot_id"),
                rs.getLong("trainer_id"),
                rs.getString("trainer_name"),
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getBigDecimal("price"),
                rs.getString("status")
        );
    }
}
