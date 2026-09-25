package com.vivekkrishnan.fitbook.repository;

import com.vivekkrishnan.fitbook.dto.HomeDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HomeRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HomeDTO.User> findAllUsers() {
        String sql = "SELECT user_id, name, email, role, created_at FROM users ORDER BY user_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new HomeDTO.User(
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ));
    }

    public List<HomeDTO.Trainer> findAllTrainers() {
        String sql = "SELECT t.trainer_id, t.user_id, t.service_id, s.service_name, t.created_at " +
                "FROM trainers t " +
                "LEFT JOIN services s ON t.service_id = s.service_id " +
                "ORDER BY t.trainer_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new HomeDTO.Trainer(
                rs.getLong("trainer_id"),
                rs.getLong("user_id"),
                (Long) rs.getObject("service_id"),
                rs.getString("service_name"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ));
    }

    public List<HomeDTO.Service> findAllServices() {
        String sql = "SELECT service_id, service_name, description, duration_minutes, price FROM services ORDER BY service_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new HomeDTO.Service(
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getString("description"),
                rs.getInt("duration_minutes"),
                rs.getBigDecimal("price")
        ));
    }
}
