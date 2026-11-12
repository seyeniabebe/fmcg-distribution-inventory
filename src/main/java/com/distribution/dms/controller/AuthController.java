package com.distribution.dms.controller;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepo;

    public AuthController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** POST /api/auth/login — { username, password } → session token + user info */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        Optional<AppUser> found = userRepo.findByUsername(username);
        if (found.isEmpty() || !found.get().getPasswordHash().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        AppUser user = found.get();
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());
        session.setAttribute("fullName", user.getFullName());

        Map<String, Object> resp = new HashMap<>();
        resp.put("username", user.getUsername());
        resp.put("fullName", user.getFullName());
        resp.put("role", user.getRole());
        resp.put("routeName", user.getRouteName());
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
        if (userRepo.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
        }
        AppUser newAgent = new AppUser(username, password, fullName, "AGENT", routeName);
        userRepo.save(newAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", newAgent.getUsername(),
                "fullName", newAgent.getFullName(),
                "role", newAgent.getRole(),
                "routeName", newAgent.getRouteName() == null ? "" : newAgent.getRouteName()
        ));
    }
}
