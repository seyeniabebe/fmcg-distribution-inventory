package com.distribution.dms.service;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only password reset service.
 *
 * Because existing credentials have been exposed (plaintext in DB + displayed
 * on login page), every existing user needs a new password before going live.
 *
 * This service:
 *   - Allows admin to reset any user's password
 *   - Stores the new password with BCrypt
 *   - Never logs the actual password or hash
 *   - Used via the /api/auth/reset-password endpoint (admin only)
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
     * Resets a user's password. Admin authorization is verified by the controller.
     *
     * @param username  the target user's username
     * @param newPassword  the new plaintext password (will be BCrypt-encoded)
     * @throws IllegalArgumentException if user not found or password too short
     */
    @Transactional
    public void resetPassword(String username, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        user.setPasswordHash(encoder.encode(newPassword.trim()));
        userRepo.save(user);

        log.info("Password reset completed for user '{}'", username);
    }
}

