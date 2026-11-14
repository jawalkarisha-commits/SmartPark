package com.smartpark.service;

import com.smartpark.entity.User;
import com.smartpark.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final SmartParkSettingsService settingsService;
    private final CustomerRegistrationService customerRegistrationService;

    public AuthService(
            UserService userService,
            JwtService jwtService,
            SmartParkSettingsService settingsService,
            CustomerRegistrationService customerRegistrationService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.settingsService = settingsService;
        this.customerRegistrationService = customerRegistrationService;
    }

    @Transactional
    public AuthResult register(
            String fullName,
            String email,
            String phone,
            String password) {
        return authenticate(
            userService.registerUser(fullName, email, password, phone)
        );
    }

    @Transactional
    public CustomerRegistrationService.PendingRegistration registerParkingOwner(
            String fullName, String email, String phone, String password) {
        if (!settingsService.getSettings().ownerRegistrationEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Parking-owner registration is currently disabled"
            );
        }
        return customerRegistrationService.startOwner(
            fullName, email, phone, password
        );
    }

    @Transactional
    public AuthResult login(String email, String password) {
        return authenticate(userService.loginUser(email, password));
    }

    private AuthResult authenticate(User user) {
        return new AuthResult(user, jwtService.issueToken(user),
            jwtService.getExpiration().toSeconds());
    }

    public record AuthResult(User user, String accessToken, long expiresInSeconds) {
    }
}
