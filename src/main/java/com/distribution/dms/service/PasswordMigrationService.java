package com.distribution.dms.service;

import com.distribution.dms.model.AppUser;
import com.distribution.dms.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time, repeatable migration: detects plaintext passwords and hashes them
 * with BCrypt. Already-hashed passwords (starting with "$2a$", "$2b$", or
 * "$2y$") are skipped.
 *
 * Controlled by property: app.security.migrate-passwords=true
 *   - Defaults to false (safe — will not run unless explicitly enabled)
 *   - After migration completes, set it back to false
 *
 * This migration does NOT print passwords or hashes to logs.
 */
@Configuration
public class PasswordMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationService.class);

    /**
     * Returns true if the stored value is already a BCrypt hash.
     * BCrypt hashes are exactly 60 characters and start with "$2a$", "$2b$", or "$2y$".
     */
    private boolean isBcryptHash(String stored) {
        return stored != null
                && stored.length() == 60
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    @Bean
    CommandLineRunner migratePasswords(AppUserRepository userRepo,
                                       BCryptPasswordEncoder encoder,
                                       org.springframework.core.env.Environment env) {
        return args -> {
            String flag = env.getProperty("app.security.migrate-passwords", "false");
            if (!"true".equalsIgnoreCase(flag)) {
                return; // Migration not requested — skip silently
            }

            log.info("Password migration: scanning for plaintext passwords...");
            migrateAllPasswords(userRepo, encoder);
            log.info("Password migration: complete. Set app.security.migrate-passwords=false and restart.");
        };
    }

    @Transactional
    public void migrateAllPasswords(AppUserRepository userRepo, BCryptPasswordEncoder encoder) {
        List<AppUser> users = userRepo.findAll();
        int migrated = 0;
        int skipped = 0;

        for (AppUser user : users) {
            if (isBcryptHash(user.getPasswordHash())) {
                skipped++;
                log.info("Password migration: user '{}' already hashed — skipped", user.getUsername());
            } else {
                String hashed = encoder.encode(user.getPasswordHash());
                user.setPasswordHash(hashed);
                userRepo.save(user);
                migrated++;
                log.info("Password migration: user '{}' migrated to BCrypt", user.getUsername());
            }
        }

        log.info("Password migration summary: {} migrated, {} already hashed, {} total",
                migrated, skipped, users.size());
    }
}

