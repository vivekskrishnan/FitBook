package com.vivekkrishnan.fitbook.repository;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// Upgrades databases created before cancelled appointments freed their slot: replaces the
// old UNIQUE(slot_id) on appointments with the "active slot" unique index from schema.sql.
@Configuration
public class SchemaMigration {

    @Bean
    ApplicationRunner migrateAppointmentSlotUniqueness(JdbcTemplate jdbc) {
        return args -> {
            if (indexExists(jdbc, "uq_appt_slot")) {
                if (!indexExists(jdbc, "idx_appt_slot")) {
                    jdbc.execute("CREATE INDEX idx_appt_slot ON appointments (slot_id)");
                }
                jdbc.execute("ALTER TABLE appointments DROP INDEX uq_appt_slot");
            }
            if (!indexExists(jdbc, "uq_appt_active_slot")) {
                jdbc.execute("ALTER TABLE appointments ADD COLUMN active_slot_id BIGINT GENERATED ALWAYS AS "
                        + "(CASE WHEN status = 'BOOKED' THEN slot_id END) STORED");
                jdbc.execute("ALTER TABLE appointments ADD CONSTRAINT uq_appt_active_slot UNIQUE (active_slot_id)");
            }
        };
    }

    private boolean indexExists(JdbcTemplate jdbc, String name) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = ?",
                Integer.class, name);
        return n != null && n > 0;
    }
}
