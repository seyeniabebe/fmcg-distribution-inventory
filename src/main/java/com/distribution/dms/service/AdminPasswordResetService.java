package com.distribution.dms.service;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing administrative password resets and user self-service password changes.
 *
 * Security rules:
 * - Minimum 8 characters.
 * - Must contain at least one letter and at least one digit.
 * - Cannot contain spaces.
 * - Cannot reuse the current password.
 * - Administrative resets set passwordResetRequired = true by default so users
 *   are forced to set their own secret password upon next login.
 * - Self-service password changes require verifying the current password and
 *   clear the passwordResetRequired flag.
 * - Passwords and hashes are NEVER logged.
 */
@Service
public class AdminPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordResetService.class);

    private final AppUserRepository userRepo;
    private final BCryptPasswordEncoder encoder;

    public AdminPasswordResetService(AppUserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    /**
     * Validates password complexity:
     * - Minimum 8 characters
     * - At least one letter and at least one digit
     * - No whitespace
     */
    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (password.contains(" ")) {
            throw new IllegalArgumentException("Password cannot contain spaces");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one letter and one number");
        }
    }

    /**
     * Administrative password reset for any user.
     *
     * @param username target username
     * @param newPassword new password
     * @param forceChangeOnNextLogin whether the user must change it on their next login
     */
    @Transactional
    public void resetPassword(String username, String newPassword, boolean forceChangeOnNextLogin) {
        validatePasswordStrength(newPassword);

        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (encoder.matches(newPassword.trim(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be identical to the previous password");
        }

        user.setPasswordHash(encoder.encode(newPassword.trim()));
        user.setPasswordResetRequired(forceChangeOnNextLogin);
        userRepo.save(user);

        log.info("Administrative password reset completed for user '{}' (forceChangeOnNextLogin={})",
                username, forceChangeOnNextLogin);
    }

    /**
     * Convenience method defaulting forceChangeOnNextLogin to true.
     */
    @Transactional
    public void resetPassword(String username, String newPassword) {
        resetPassword(username, newPassword, true);
    }

    /**
     * Self-service password change for currently authenticated user.
     * Requires verifying current password and clears the force-change flag.
     *
     * @param username target username
     * @param currentPassword user's current password
     * @param newPassword user's new desired password
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        validatePasswordStrength(newPassword);

        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!encoder.matches(currentPassword.trim(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (encoder.matches(newPassword.trim(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be identical to the current password");
        }

        user.setPasswordHash(encoder.encode(newPassword.trim()));
        user.setPasswordResetRequired(false);
        userRepo.save(user);

        log.info("Self-service password change completed for user '{}'", username);
    }
}
