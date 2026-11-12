package com.distribution.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Provides a BCryptPasswordEncoder bean for password hashing.
 *
 * We use spring-security-crypto only (NOT spring-boot-starter-security)
 * to avoid overriding the existing custom session-based auth and
 * controller-level RBAC.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

