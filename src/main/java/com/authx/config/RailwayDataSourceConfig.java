package com.authx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Custom DataSource configuration for Railway
 * Handles parsing Railway's DATABASE_URL format
 */
@Configuration
@Profile("railway")
@Slf4j
public class RailwayDataSourceConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        log.info("Configuring DataSource from Railway DATABASE_URL");
        log.info("DATABASE_URL format: {}", maskPassword(databaseUrl));
        
        try {
            // Parse Railway's DATABASE_URL: postgresql://user:password@host:port/database
            URI uri = new URI(databaseUrl);
            
            String host = uri.getHost();
            int port = uri.getPort();
            String database = uri.getPath().substring(1); // Remove leading '/'
            String[] userInfo = uri.getUserInfo().split(":");
            String username = userInfo[0];
            String password = userInfo[1];
            
            // Build JDBC URL
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            
            log.info("Parsed JDBC URL: {}", jdbcUrl);
            log.info("Username: {}", username);
            log.info("Host: {}", host);
            log.info("Port: {}", port);
            log.info("Database: {}", database);
            
            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse DATABASE_URL: {}", databaseUrl, e);
            throw new RuntimeException("Failed to configure DataSource from DATABASE_URL", e);
        }
    }
    
    private String maskPassword(String url) {
        if (url == null) return "null";
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }
}
