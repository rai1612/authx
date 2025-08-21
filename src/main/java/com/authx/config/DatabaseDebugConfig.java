package com.authx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import jakarta.annotation.PostConstruct;

/**
 * Debug configuration to log database connection details
 */
@Configuration
@Profile("railway")
@Slf4j
public class DatabaseDebugConfig {

    @Value("${PGHOST:NOT_SET}")
    private String pgHost;

    @Value("${PGPORT:NOT_SET}")
    private String pgPort;

    @Value("${PGDATABASE:NOT_SET}")
    private String pgDatabase;

    @Value("${PGUSER:NOT_SET}")
    private String pgUser;

    @Value("${DATABASE_URL:NOT_SET}")
    private String databaseUrl;

    @Value("${REDIS_URL:NOT_SET}")
    private String redisUrl;

    @PostConstruct
    public void logDatabaseConfig() {
        log.info("=== RAILWAY DATABASE CONFIGURATION DEBUG ===");
        log.info("PGHOST: {}", pgHost);
        log.info("PGPORT: {}", pgPort);
        log.info("PGDATABASE: {}", pgDatabase);
        log.info("PGUSER: {}", pgUser);
        log.info("DATABASE_URL: {}", maskPassword(databaseUrl));
        log.info("REDIS_URL: {}", maskPassword(redisUrl));
        log.info("Final JDBC URL will be: jdbc:postgresql://{}:{}/{}", pgHost, pgPort, pgDatabase);
        log.info("=== END DATABASE DEBUG ===");
    }

    private String maskPassword(String url) {
        if (url == null || url.equals("NOT_SET")) {
            return url;
        }
        // Mask password in URL for security
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }
}
