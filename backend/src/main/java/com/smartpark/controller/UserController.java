package com.smartpark.controller;

import com.smartpark.entity.User;
import com.smartpark.security.CurrentUser;
import com.smartpark.service.AuthService;
import com.smartpark.service.CustomerRegistrationService;
import com.smartpark.service.PasswordResetService;
import com.smartpark.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final AuthService authService;
    private final CustomerRegistrationService customerRegistrationService;

    public UserController(
            UserService userService,
            PasswordResetService passwordResetService,
            AuthService authService,
            CustomerRegistrationService customerRegistrationService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.authService = authService;
        this.customerRegistrationService = customerRegistrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CustomerRegistrationService.PendingRegistration register(
            @Valid @RequestBody RegisterRequest request) {
        return customerRegistrationService.start(
            request.fullName(), request.email(), null, request.password()
        );
    }

    @PostMapping("/login")
    public AuthController.AuthResponse login(
            @Valid @RequestBody LoginRequest request) {
        return AuthController.AuthResponse.from(authService.login(
            request.email(), request.password()
        ));
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return UserResponse.from(userService.getUser(currentUser.id()));
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUser(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.updateProfile(
            currentUser.id(), request.fullName(), request.phone()
        ));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
            currentUser.id(), request.currentPassword(), request.newPassword()
        );
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendResetLink(request.email());
        return new MessageResponse(
            "If an account exists for that email, a password reset link will be sent."
        );
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Password reset successfully.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    public record RegisterRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
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

    public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Pattern(
            regexp = "^[+() .\\-\\d]{7,30}$",
            message = "Enter a valid phone number"
        ) String phone
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d).{6,}$",
            message = "Password must be at least 6 characters and include an uppercase letter and a number"
        ) String newPassword
    ) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d).{6,}$",
            message = "Password must be at least 6 characters and include an uppercase letter and a number"
        ) String newPassword
    ) {
    }

    public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled
    ) {
        static UserResponse from(User user) {
            return new UserResponse(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.isEnabled()
            );
        }
    }

    public record ErrorResponse(String message) {
    }

    public record MessageResponse(String message) {
    }
}
