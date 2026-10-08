package com.vivekkrishnan.fitbook.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public AppUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        List<AppUserPrincipal> results = jdbcTemplate.query(
                "SELECT user_id, name, email, password_hash, role FROM users WHERE email = ?",
                (rs, rowNum) -> new AppUserPrincipal(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("role")
                ),
                email
        );
        if (results.isEmpty()) {
            throw new UsernameNotFoundException("No user with email " + email);
        }
        return results.get(0);
    }
}
