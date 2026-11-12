package com.distribution.dms.controller;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import com.distribution.dms.service.AdminPasswordResetService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminPasswordResetService resetService;

    public AuthController(AppUserRepository userRepo,
                          BCryptPasswordEncoder passwordEncoder,
                          AdminPasswordResetService resetService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.resetService = resetService;
    }

    // ────────────────────────── HELPER ──────────────────────────

    /**
     * Authenticates the user using BCrypt password hashing.
     * Strictly requires a valid BCrypt hash — legacy plaintext fallback has been removed.
     */
    private boolean passwordMatches(String rawPassword, String storedHash) {
        if (storedHash == null || rawPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    // ────────────────────────── ENDPOINTS ──────────────────────────

    /** POST /api/auth/login — { username, password } → session token + user info */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        Optional<AppUser> found = userRepo.findByUsername(username);
        if (found.isEmpty() || !passwordMatches(password, found.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        AppUser user = found.get();
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());
        session.setAttribute("fullName", user.getFullName());
        session.setAttribute("mustChangePassword", user.isPasswordResetRequired());

        Map<String, Object> resp = new HashMap<>();
        resp.put("username", user.getUsername());
        resp.put("fullName", user.getFullName());
        resp.put("role", user.getRole());
        resp.put("routeName", user.getRouteName());
        resp.put("mustChangePassword", user.isPasswordResetRequired());
        resp.put("sessionId", session.getId());
        return ResponseEntity.ok(resp);
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    /** GET /api/auth/me — returns current session user info */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("username", session.getAttribute("username"));
        resp.put("fullName", session.getAttribute("fullName"));
        resp.put("role", role);
        resp.put("mustChangePassword", session.getAttribute("mustChangePassword") != null
                ? session.getAttribute("mustChangePassword") : false);
        resp.put("sessionId", session.getId());
        return ResponseEntity.ok(resp);
    }

    /** GET /api/auth/agents — ADMIN only: list all agent accounts */
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        var agents = userRepo.findAll().stream()
                .filter(u -> "AGENT".equals(u.getRole()))
                .map(u -> Map.of(
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "routeName", u.getRouteName() == null ? "" : u.getRouteName()
                ))
                .toList();
        return ResponseEntity.ok(agents);
    }

    /** POST /api/auth/create-agent — ADMIN only: create a new agent account */
    @PostMapping("/create-agent")
    public ResponseEntity<?> createAgent(@RequestBody Map<String, String> body, HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String fullName = body.getOrDefault("fullName", "").trim();
        String routeName = body.getOrDefault("routeName", "").trim();
        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username, password, and fullName are required"));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }
        if (userRepo.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
        }
        // Store password with BCrypt — never plaintext
        String hashedPassword = passwordEncoder.encode(password);
        AppUser newAgent = new AppUser(username, hashedPassword, fullName, "AGENT", routeName);
        userRepo.save(newAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", newAgent.getUsername(),
                "fullName", newAgent.getFullName(),
                "role", newAgent.getRole(),
                "routeName", newAgent.getRouteName() == null ? "" : newAgent.getRouteName()
        ));
    }

    /** POST /api/auth/reset-password — ADMIN only: reset any user's password */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body, HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        String username = body.getOrDefault("username", "").trim();
        String newPassword = body.getOrDefault("newPassword", "").trim();
        boolean forceChange = body.get("forceChangeOnNextLogin") == null
                || Boolean.parseBoolean(body.get("forceChangeOnNextLogin"));

        if (username.isEmpty() || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and newPassword are required"));
        }
        try {
            resetService.resetPassword(username, newPassword, forceChange);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successful for user: " + username,
                    "forceChangeOnNextLogin", forceChange
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/auth/change-password — Self-service password change for authenticated users */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        String currentPassword = body.getOrDefault("currentPassword", "").trim();
        String newPassword = body.getOrDefault("newPassword", "").trim();
        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "currentPassword and newPassword are required"));
        }
        try {
            resetService.changePassword(username, currentPassword, newPassword);
            session.setAttribute("mustChangePassword", false);
            return ResponseEntity.ok(Map.of(
                    "message", "Password changed successfully",
                    "mustChangePassword", false
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
