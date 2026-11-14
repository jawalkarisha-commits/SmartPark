package com.smartpark.controller;

import com.smartpark.entity.User;
import com.smartpark.service.AuthService;
import com.smartpark.service.CustomerRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CustomerRegistrationService customerRegistrationService;

    public AuthController(
            AuthService authService,
            CustomerRegistrationService customerRegistrationService) {
        this.authService = authService;
        this.customerRegistrationService = customerRegistrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CustomerRegistrationService.PendingRegistration register(
            @Valid @RequestBody RegisterRequest request) {
        return customerRegistrationService.start(
            request.fullName(), request.email(), request.phone(), request.password()
        );
    }

    @PostMapping("/register/verify")
    public AuthResponse verifyRegistration(
            @Valid @RequestBody RegistrationVerificationRequest request) {
        return AuthResponse.from(customerRegistrationService.verify(
            request.email(), request.otp()
        ));
    }

    @PostMapping("/register/resend")
    public CustomerRegistrationService.PendingRegistration resendRegistrationCode(
            @Valid @RequestBody RegistrationResendRequest request) {
        return customerRegistrationService.resend(request.email());
    }

    @PostMapping("/register/owner")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerParkingOwner(
            @Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(authService.registerParkingOwner(
            request.fullName(), request.email(), request.phone(), request.password()
        ));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(
            authService.login(request.email(), request.password())
        );
    }

    public record RegisterRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(
            regexp = "^[+() .\\-\\d]{7,30}$",
            message = "Enter a valid phone number"
        ) String phone,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d).{6,}$",
            message = "Password must be at least 6 characters and include an uppercase letter and a number"
        ) String password
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record RegistrationVerificationRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String otp
    ) {
    }

    public record RegistrationResendRequest(@NotBlank @Email String email) {
    }

    public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long id,
        String fullName,
        String email,
        String phone,
        String role
    ) {
        static AuthResponse from(AuthService.AuthResult result) {
            User user = result.user();
            return new AuthResponse(
                result.accessToken(), "Bearer", result.expiresInSeconds(),
                user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole()
            );
        }
    }
}
