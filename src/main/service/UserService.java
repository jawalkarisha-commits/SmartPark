package com.smartpark.service;

import com.smartpark.entity.User;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User registerUser(String fullName, String email, String password) {

        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                "Email is already registered"
            );
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.hashPassword(password));
        user.setRole("USER");

        return repository.save(user);
    }

    public User loginUser(String email, String password) {

        User user = repository.findByEmail(email)
            .orElseThrow(() ->
                new IllegalArgumentException("Invalid email or password")
            );

        if (!PasswordUtil.verifyPassword(
                password, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                "Invalid email or password"
            );
        }

        return user;
    }
}