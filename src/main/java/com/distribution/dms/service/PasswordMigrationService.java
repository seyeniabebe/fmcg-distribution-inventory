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
            String migrateFlag = env.getProperty("app.security.migrate-passwords", "false");
            if ("true".equalsIgnoreCase(migrateFlag)) {
                log.info("Password migration: scanning for plaintext passwords...");
                migrateAllPasswords(userRepo, encoder);
                log.info("Password migration: complete. Set app.security.migrate-passwords=false.");
            }

            String rotateFlag = env.getProperty("app.security.rotate-compromised-passwords", "false");
            if ("true".equalsIgnoreCase(rotateFlag)) {
                log.info("Compromised password rotation: invalidating compromised credentials and generating secure temporary passwords...");
                String outputFile = env.getProperty("app.security.credentials-output-file", "data/initial_credentials_distribution.txt");
                rotateCompromisedCredentials(userRepo, encoder, outputFile);
                log.info("Compromised password rotation complete. Temporary credentials saved to {}", outputFile);
            }
        };
    }

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public static String generateSecureTemporaryPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        for (int i = 6; i < length; i++) {
            sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    @Transactional
    public void rotateCompromisedCredentials(AppUserRepository userRepo, BCryptPasswordEncoder encoder, String outputFilePath) {
        List<AppUser> users = userRepo.findAll();
        StringBuilder report = new StringBuilder();
        report.append("================================================================================\n");
        report.append("FMCG DMS - ONE-TIME INITIAL TEMPORARY CREDENTIAL DISTRIBUTION\n");
        report.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n");
        report.append("SECURITY NOTICE: Distribute to authorized staff only. Each user is required\n");
        report.append("to set a new permanent password immediately upon their first login.\n");
        report.append("================================================================================\n\n");

        for (AppUser user : users) {
            String tempPassword = generateSecureTemporaryPassword(14);
            user.setPasswordHash(encoder.encode(tempPassword));
            user.setPasswordResetRequired(true);
            userRepo.save(user);

            report.append("Username:       ").append(user.getUsername()).append("\n");
            report.append("Full Name:      ").append(user.getFullName()).append("\n");
            report.append("Role:           ").append(user.getRole()).append("\n");
            report.append("Route/Area:     ").append(user.getRouteName() == null ? "None (HQ)" : user.getRouteName()).append("\n");
            report.append("Temporary Pass: ").append(tempPassword).append("\n");
            report.append("Force Reset:    REQUIRED ON FIRST LOGIN\n");
            report.append("--------------------------------------------------------------------------------\n\n");
        }

        try {
            java.nio.file.Path path = java.nio.file.Paths.get(outputFilePath).toAbsolutePath().normalize();
            if (path.getParent() != null && !java.nio.file.Files.exists(path.getParent())) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            java.nio.file.Files.writeString(path, report.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            try {
                // Set file permission to 600 (owner read/write only) on POSIX filesystems
                java.nio.file.Files.setPosixFilePermissions(path, java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                ));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystem (Windows)
            }
        } catch (java.io.IOException e) {
            log.error("Failed to write credential distribution file: {}", e.getMessage());
            throw new RuntimeException("Could not save initial credential distribution file", e);
        }
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

