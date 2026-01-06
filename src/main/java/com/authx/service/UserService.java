package com.authx.service;

import com.authx.dto.RegisterRequest;
import com.authx.model.AuditLog;
import com.authx.model.Role;
import com.authx.model.User;
import com.authx.model.UserRole;
import com.authx.repository.RoleRepository;
import com.authx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PhoneValidationService phoneValidationService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            AuditService auditService, PhoneValidationService phoneValidationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.phoneValidationService = phoneValidationService;
    }

    @Transactional
    public User createUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Validate and normalize phone number if provided
        String normalizedPhone = null;
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            PhoneValidationService.ValidationResult validation = phoneValidationService
                    .validateWithDetails(request.getPhoneNumber());
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid phone number: " + validation.getMessage());
            }
            normalizedPhone = validation.getNormalizedNumber();

            // Check if phone number is already in use
            if (userRepository.existsByPhoneNumber(normalizedPhone)) {
                throw new IllegalArgumentException("Phone number already exists");
            }

            log.info("Phone number validated and normalized: {} -> {}",
                    phoneValidationService.maskPhoneNumber(request.getPhoneNumber()),
                    phoneValidationService.maskPhoneNumber(normalizedPhone));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(normalizedPhone);
        user.setStatus(User.UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Assign default USER role
        assignDefaultRole(savedUser);

        log.info("User created: {}", savedUser.getEmail());
        return savedUser;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByIdentifier(String identifier) {
        return userRepository.findByUsernameOrEmail(identifier, identifier);
    }

    public Optional<User> findByPasswordResetToken(String token) {
        return userRepository.findByPasswordResetToken(token);
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        userRepository.updateLastLogin(userId, LocalDateTime.now());
    }

    @Transactional
    public void incrementFailedAttempts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int attempts = user.getFailedLoginAttempts() + 1;
        userRepository.updateFailedLoginAttempts(userId, attempts);

        if (attempts >= 5) {
            lockUser(userId);
            // Log suspicious activity for account lockout
            auditService.logEventSync(user, AuditLog.EventType.SUSPICIOUS_ACTIVITY,
                    "Account locked due to multiple failed login attempts: " + attempts);
        } else if (attempts >= 3) {
            // Log suspicious activity for multiple failed attempts
            auditService.logEventSync(user, AuditLog.EventType.SUSPICIOUS_ACTIVITY,
                    "Multiple failed login attempts detected: " + attempts);
        }
    }

    @Transactional
    public void resetFailedAttempts(Long userId) {
        userRepository.updateFailedLoginAttempts(userId, 0);
    }

    @Transactional
    public void lockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDateTime lockUntil = LocalDateTime.now().plusHours(1);
        user.setLockedUntil(lockUntil);
        user.setStatus(User.UserStatus.LOCKED);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.ACCOUNT_LOCKED, "Account locked due to failed login attempts");
    }

    public boolean isUserLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void autoUnlockExpiredUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Only auto-unlock if the lock time has actually expired
        if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            user.setLockedUntil(null);
            user.setStatus(User.UserStatus.ACTIVE);
            user.setFailedLoginAttempts(0); // Reset failed attempts on auto-unlock
            userRepository.save(user);

            auditService.logEvent(user, AuditLog.EventType.ACCOUNT_UNLOCKED,
                    "Account automatically unlocked after lock period expired");
            log.info("User {} automatically unlocked after lock period expired", user.getEmail());
        }
    }

    private void assignDefaultRole(User user) {
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> createDefaultRole("USER", "Default user role"));

        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(userRole);
        user.getRoles().add(assignment);
    }

    @Transactional
    public void deleteUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        auditService.logEvent(user, AuditLog.EventType.USER_DELETION, "User account deleted");
        userRepository.delete(user);
        log.info("User deleted: {}", email);
    }

    @Transactional
    public void enableMfa(Long userId, User.MfaMethod preferredMethod) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setMfaEnabled(true);
        user.setPreferredMfaMethod(preferredMethod);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.MFA_ENABLED, "MFA enabled with method: " + preferredMethod);
        log.info("MFA enabled for user: {} with method: {}", user.getEmail(), preferredMethod);
    }

    @Transactional
    public void disableMfa(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setMfaEnabled(false);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.MFA_DISABLED, "MFA disabled");
        log.info("MFA disabled for user: {}", user.getEmail());
    }

    @Transactional
    public void updateMfaMethod(Long userId, User.MfaMethod method) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPreferredMfaMethod(method);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.MFA_METHOD_UPDATED, "MFA method changed to: " + method);
        log.info("MFA method updated for user: {} to: {}", user.getEmail(), method);
    }

    public boolean verifyPassword(User user, String password) {
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(encodedPassword);
        userRepository.save(user);

        log.info("Password updated for user: {}", user.getEmail());
    }

    @Transactional
    public void updateProfile(Long userId, String username, String email, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean hasChanges = false;
        StringBuilder changes = new StringBuilder();

        // Update username if provided and different
        if (username != null && !username.trim().isEmpty() && !username.equals(user.getUsername())) {
            if (userRepository.existsByUsernameAndIdNot(username, userId)) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUsername(username.trim());
            changes.append("username, ");
            hasChanges = true;
        }

        // Update email if provided and different
        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(email, userId)) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(email.trim().toLowerCase());
            changes.append("email, ");
            hasChanges = true;
        }

        // Update phone number if provided and different
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            PhoneValidationService.ValidationResult validation = phoneValidationService
                    .validateWithDetails(phoneNumber);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid phone number: " + validation.getMessage());
            }

            String normalizedPhone = validation.getNormalizedNumber();
            if (!normalizedPhone.equals(user.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumberAndIdNot(normalizedPhone, userId)) {
                    throw new IllegalArgumentException("Phone number already exists");
                }
                user.setPhoneNumber(normalizedPhone);
                changes.append("phone, ");
                hasChanges = true;
            }
        } else if (phoneNumber != null && phoneNumber.trim().isEmpty() && user.getPhoneNumber() != null) {
            // Remove phone number if empty string is provided
            user.setPhoneNumber(null);
            changes.append("phone (removed), ");
            hasChanges = true;
        }

        if (hasChanges) {
            userRepository.save(user);

            // Remove trailing comma and space
            String changeList = changes.toString().replaceAll(", $", "");
            auditService.logEvent(user, AuditLog.EventType.PROFILE_UPDATED,
                    "Profile updated: " + changeList);

            log.info("Profile updated for user: {} - changes: {}", user.getEmail(), changeList);
        }
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    private Role createDefaultRole(String name, String description) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }
}