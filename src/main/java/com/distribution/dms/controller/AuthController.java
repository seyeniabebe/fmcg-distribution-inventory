package com.distribution.dms.controller;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import com.distribution.dms.repository.SaleTransactionRepository;
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
    private final SaleTransactionRepository saleRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminPasswordResetService resetService;

    public AuthController(AppUserRepository userRepo,
                          SaleTransactionRepository saleRepo,
                          BCryptPasswordEncoder passwordEncoder,
                          AdminPasswordResetService resetService) {
        this.userRepo = userRepo;
        this.saleRepo = saleRepo;
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

    private boolean isAdmin(HttpSession session) {
        return "ADMIN".equals(session.getAttribute("role"));
    }

    private boolean isPasswordChangeRequired(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("mustChangePassword"));
    }

    // ────────────────────────── AUTH ENDPOINTS ──────────────────────────

    /** POST /api/auth/login — { username, password } → session token + user info */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   jakarta.servlet.http.HttpServletRequest request) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        Optional<AppUser> found = userRepo.findByUsername(username);
        if (found.isEmpty() || !passwordMatches(password, found.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        AppUser user = found.get();

        // Check account activation status
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Account has been deactivated. Please contact your system administrator."));
        }

        // Session fixation protection: invalidate any pre-existing session and create fresh session
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);

        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());
        session.setAttribute("fullName", user.getFullName());
        session.setAttribute("mustChangePassword", user.isPasswordResetRequired());
        session.setAttribute("canAddStock", user.isCanAddStock());

        Map<String, Object> resp = new HashMap<>();
        resp.put("username", user.getUsername());
        resp.put("fullName", user.getFullName());
        resp.put("role", user.getRole());
        resp.put("routeName", user.getRouteName());
        resp.put("mustChangePassword", user.isPasswordResetRequired());
        resp.put("canAddStock", user.isCanAddStock());
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
        String username = (String) session.getAttribute("username");
        if (role == null || username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        // Verify active status in database
        Optional<AppUser> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            session.invalidate();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Account is inactive or no longer exists"));
        }

        AppUser user = userOpt.get();
        session.setAttribute("canAddStock", user.isCanAddStock());
        Map<String, Object> resp = new HashMap<>();
        resp.put("username", user.getUsername());
        resp.put("fullName", user.getFullName());
        resp.put("role", user.getRole());
        resp.put("routeName", user.getRouteName());
        resp.put("mustChangePassword", session.getAttribute("mustChangePassword") != null
                ? session.getAttribute("mustChangePassword") : user.isPasswordResetRequired());
        resp.put("canAddStock", user.isCanAddStock());
        resp.put("sessionId", session.getId());
        return ResponseEntity.ok(resp);
    }

    // ────────────────────────── AGENT MANAGEMENT ENDPOINTS ──────────────────────────

    /** GET /api/auth/agents — ADMIN only: list all agent accounts with status and statistics */
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        var agents = userRepo.findAll().stream()
                .filter(u -> "AGENT".equals(u.getRole()))
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("fullName", u.getFullName());
                    map.put("routeName", u.getRouteName() == null ? "" : u.getRouteName());
                    map.put("active", u.isActive());
                    map.put("role", u.getRole());
                    map.put("passwordResetRequired", u.isPasswordResetRequired());
                    map.put("canAddStock", u.isCanAddStock());
                    map.put("salesCount", saleRepo.countByAgentName(u.getFullName()));
                    return map;
                })
                .toList();
        return ResponseEntity.ok(agents);
    }

    /** POST /api/auth/create-agent — ADMIN only: create a new agent account */
    @PostMapping("/create-agent")
    public ResponseEntity<?> createAgent(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String fullName = body.getOrDefault("fullName", "").trim();
        String routeName = body.getOrDefault("routeName", "").trim();
        boolean forceChange = body.get("forceChangeOnNextLogin") != null
                && Boolean.parseBoolean(body.get("forceChangeOnNextLogin"));
        boolean canAddStock = body.get("canAddStock") != null
                && Boolean.parseBoolean(body.get("canAddStock"));

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
        AppUser newAgent = new AppUser(username, hashedPassword, fullName, "AGENT", routeName, forceChange, true, canAddStock);
        userRepo.save(newAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", newAgent.getId(),
                "username", newAgent.getUsername(),
                "fullName", newAgent.getFullName(),
                "role", newAgent.getRole(),
                "routeName", newAgent.getRouteName() == null ? "" : newAgent.getRouteName(),
                "active", newAgent.isActive(),
                "canAddStock", newAgent.isCanAddStock()
        ));
    }

    /** PUT /api/auth/agents/{id} — ADMIN only: update agent details */
    @PutMapping("/agents/{id}")
    public ResponseEntity<?> updateAgent(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        Optional<AppUser> userOpt = userRepo.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Agent not found"));
        }
        AppUser agent = userOpt.get();

        // Safety check: Cannot modify admin through agent update
        if ("ADMIN".equals(agent.getRole())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Cannot modify administrator through agent management"));
        }

        String fullName = body.get("fullName");
        String routeName = body.get("routeName");
        if (fullName != null && !fullName.trim().isEmpty()) {
            agent.setFullName(fullName.trim());
        }
        if (routeName != null) {
            agent.setRouteName(routeName.trim());
        }
        if (body.get("canAddStock") != null) {
            agent.setCanAddStock(Boolean.parseBoolean(body.get("canAddStock")));
        }
        userRepo.save(agent);

        return ResponseEntity.ok(Map.of(
                "id", agent.getId(),
                "username", agent.getUsername(),
                "fullName", agent.getFullName(),
                "routeName", agent.getRouteName() == null ? "" : agent.getRouteName(),
                "active", agent.isActive(),
                "canAddStock", agent.isCanAddStock()
        ));
    }

    /** POST /api/auth/agents/{id}/deactivate — ADMIN only: deactivate agent account */
    @PostMapping("/agents/{id}/deactivate")
    public ResponseEntity<?> deactivateAgent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        Optional<AppUser> userOpt = userRepo.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Agent not found"));
        }
        AppUser agent = userOpt.get();

        // Safety check: Never deactivate administrator account
        if ("ADMIN".equals(agent.getRole())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Administrator account cannot be deactivated"));
        }

        agent.setActive(false);
        userRepo.save(agent);

        return ResponseEntity.ok(Map.of(
                "id", agent.getId(),
                "username", agent.getUsername(),
                "fullName", agent.getFullName(),
                "active", false,
                "message", "Agent account deactivated successfully"
        ));
    }

    /** POST /api/auth/agents/{id}/reactivate — ADMIN only: reactivate agent account */
    @PostMapping("/agents/{id}/reactivate")
    public ResponseEntity<?> reactivateAgent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        Optional<AppUser> userOpt = userRepo.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Agent not found"));
        }
        AppUser agent = userOpt.get();

        agent.setActive(true);
        userRepo.save(agent);

        return ResponseEntity.ok(Map.of(
                "id", agent.getId(),
                "username", agent.getUsername(),
                "fullName", agent.getFullName(),
                "active", true,
                "message", "Agent account reactivated successfully"
        ));
    }

    /** DELETE /api/auth/agents/{id} — ADMIN only: delete agent ONLY if no business records exist */
    @DeleteMapping("/agents/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        Optional<AppUser> userOpt = userRepo.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Agent not found"));
        }
        AppUser agent = userOpt.get();

        // Safety rule 1: Never delete administrator account
        if ("ADMIN".equals(agent.getRole())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Administrator account cannot be deleted"));
        }

        // Safety rule 2: Check for existing sales records
        long salesCount = saleRepo.countByAgentName(agent.getFullName());
        if (salesCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot delete agent '" + agent.getFullName() + "' because they have " + salesCount +
                            " historical sales record(s). Deactivate the agent instead to preserve audit history and reporting."
            ));
        }

        userRepo.delete(agent);
        return ResponseEntity.ok(Map.of("message", "Agent account deleted successfully"));
    }

    /** POST /api/auth/agents/{id}/reset-password — ADMIN only: reset specific agent password */
    @PostMapping("/agents/{id}/reset-password")
    public ResponseEntity<?> resetAgentPasswordById(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body,
                                                    HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
        }
        Optional<AppUser> userOpt = userRepo.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Agent not found"));
        }
        AppUser agent = userOpt.get();
        String newPassword = body.getOrDefault("newPassword", "").trim();
        boolean forceChange = body.get("forceChangeOnNextLogin") == null
                || Boolean.parseBoolean(body.get("forceChangeOnNextLogin"));

        if (newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPassword is required"));
        }
        try {
            resetService.resetPassword(agent.getUsername(), newPassword, forceChange);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successful for agent: " + agent.getFullName(),
                    "username", agent.getUsername(),
                    "forceChangeOnNextLogin", forceChange
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/auth/reset-password — ADMIN only: reset any user's password */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        if (isPasswordChangeRequired(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Password change required before accessing application", "mustChangePassword", true));
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
