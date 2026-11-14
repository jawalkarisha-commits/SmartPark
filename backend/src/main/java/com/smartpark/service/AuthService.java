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
    private final OwnerVerificationService ownerVerificationService;

    public AuthService(
            UserService userService,
            JwtService jwtService,
            SmartParkSettingsService settingsService,
            OwnerVerificationService ownerVerificationService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.settingsService = settingsService;
        this.ownerVerificationService = ownerVerificationService;
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
    public AuthResult registerParkingOwner(
            String fullName, String email, String phone, String password) {
        if (!settingsService.getSettings().ownerRegistrationEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Parking-owner registration is currently disabled"
            );
        }
        User owner = userService.registerUser(
            fullName, email, password, phone, "PARKING_OWNER"
        );
        ownerVerificationService.initializeOwner(owner);
        return authenticate(owner);
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
