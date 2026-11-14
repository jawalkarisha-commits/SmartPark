package com.smartpark.service;

import com.smartpark.entity.User;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.PasswordPolicy;
import com.smartpark.security.PasswordUtil;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String fullName, String email, String password) {
        return registerUser(fullName, email, password, null);
    }

    @Transactional
    public User registerUser(
            String fullName,
            String email,
            String password,
            String phone) {
        return registerUser(fullName, email, password, phone, "USER");
    }

    @Transactional
    public User registerUser(
            String fullName,
            String email,
            String password,
            String phone,
            String role) {

        PasswordPolicy.validate(password);
        if (!"USER".equals(role) && !"PARKING_OWNER".equals(role)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Public registration cannot create administrator accounts"
            );
        }
        String normalizedEmail = normalizeEmail(email);

        if (repository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                "Email is already registered"
            );
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);

        return repository.save(user);
    }

    @Transactional
    public User loginUser(String email, String password) {

        User user = repository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid email or password"
                )
            );

        if (!user.isEnabled() || !PasswordUtil.verifyPassword(
                password, user.getPasswordHash())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid email or password"
            );
        }

        if (!PasswordUtil.isBcryptHash(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return repository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));
    }

    @Transactional
    public User updateProfile(Long userId, String fullName, String phone) {
        User user = getUser(userId);
        user.setFullName(fullName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        return user;
    }

    @Transactional
    public void changePassword(
            Long userId,
            String currentPassword,
            String newPassword) {
        PasswordPolicy.validate(newPassword);
        User user = getUser(userId);
        if (!PasswordUtil.verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Current password is incorrect"
            );
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
