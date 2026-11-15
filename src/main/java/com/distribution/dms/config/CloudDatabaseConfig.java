package com.distribution.dms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Handles cloud database URL conversion for platforms like Render and Railway.
 *
 * Cloud platforms typically provide DATABASE_URL in standard URI format:
 *   postgres://user:password@host:port/database
 *
 * This configuration automatically converts it to the JDBC format required by Spring Boot:
 *   jdbc:postgresql://host:port/database
 * and extracts the username/password credentials.
 *
 * If DATABASE_URL is not set (e.g. local dev, unit/integration tests),
 * it seamlessly delegates to standard Spring Boot datasource properties.
 */
@Configuration
public class CloudDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudDatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties, Environment env) {
        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.startsWith("jdbc:")) {
            try {
                URI uri = new URI(databaseUrl);
                String userInfo = uri.getUserInfo();
                String username = properties.getUsername();
                String password = properties.getPassword();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String dbPath = uri.getPath();
                if (dbPath != null && dbPath.startsWith("/")) {
                    dbPath = dbPath.substring(1);
                }
                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", uri.getHost(), port, dbPath);
                log.info("Converted cloud DATABASE_URL to JDBC: host='{}', port={}, db='{}'",
                        uri.getHost(), port, dbPath);

                HikariDataSource ds = new HikariDataSource();
                ds.setDriverClassName("org.postgresql.Driver");
                ds.setJdbcUrl(jdbcUrl);
                ds.setUsername(username);
                ds.setPassword(password);
                return ds;
            } catch (Exception e) {
                log.warn("Failed to parse DATABASE_URL as URI: {}. Falling back to standard properties.", e.getMessage());
            }
        }
        return properties.initializeDataSourceBuilder().build();
    }
}

