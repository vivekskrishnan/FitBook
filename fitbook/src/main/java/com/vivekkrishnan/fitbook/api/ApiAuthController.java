package com.vivekkrishnan.fitbook.api;

import com.vivekkrishnan.fitbook.security.AppUserPrincipal;
import com.vivekkrishnan.fitbook.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// JWT login. POST /api/auth/login is the only public API endpoint besides browsing slots.
@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {

    public record LoginRequest(String email, String password) {}

    public record UserInfo(Long id, String name, String email, String role) {
        static UserInfo of(AppUserPrincipal user) {
            return new UserInfo(user.getUserId(), user.getName(), user.getUsername(), user.getRole());
        }
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds, UserInfo user) {}

    private final JwtService jwtService;

    public ApiAuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required."));
        }
        try {
            JwtService.IssuedToken issued = jwtService.login(request.email().strip(), request.password());
            return ResponseEntity.ok(new LoginResponse(issued.token(), "Bearer", issued.expiresInSeconds(),
                    UserInfo.of(issued.user())));
        } catch (AuthenticationException e) {
            // Same message for "no such user" and "wrong password" so emails cannot be probed.
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password."));
        }
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserInfo.of(principal);
    }
}
