package com.authx.config;

import com.authx.model.Role;
import com.authx.model.User;
import com.authx.model.UserRole;
import com.authx.repository.RoleRepository;
import com.authx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeAdminUser();
    }

    private void initializeRoles() {
        // Create USER role if it doesn't exist
        if (roleRepository.findByName("USER").isEmpty()) {
            Role userRole = new Role();
            userRole.setName("USER");
            userRole.setDescription("Default user role with basic permissions");
            roleRepository.save(userRole);
            log.info("Created USER role");
        }

        // Create ADMIN role if it doesn't exist
        if (roleRepository.findByName("ADMIN").isEmpty()) {
            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("Administrator role with full system access");
            roleRepository.save(adminRole);
            log.info("Created ADMIN role");
        }
    }

    private void initializeAdminUser() {
        // Check if any admin user exists
        String adminEmail = "admin@authx.local";
        
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            // Create default admin user
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            admin.setStatus(User.UserStatus.ACTIVE);
            admin.setMfaEnabled(false);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());

            User savedAdmin = userRepository.save(admin);

            // Assign ADMIN role
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
            UserRole userRole = new UserRole();
            userRole.setUser(savedAdmin);
            userRole.setRole(adminRole);
            userRole.setAssignedAt(LocalDateTime.now());

            savedAdmin.getRoles().add(userRole);
            userRepository.save(savedAdmin);

            log.info("✅ Default admin user created!");
            log.info("📧 Email: {}", adminEmail);
            log.info("🔑 Password: AdminPass123!");
            log.info("⚠️  Please change the default password after first login!");
        }
    }
}