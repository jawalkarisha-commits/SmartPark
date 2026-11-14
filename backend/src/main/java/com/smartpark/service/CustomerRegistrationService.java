package com.smartpark.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartpark.entity.CustomerRegistration;
import com.smartpark.entity.User;
import com.smartpark.repository.CustomerRegistrationRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.JwtService;
import com.smartpark.security.PasswordPolicy;
import java.time.LocalDateTime;
import java.util.Locale;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerRegistrationService {

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmartParkMailDeliveryService mailDeliveryService;
    private final JwtService jwtService;
    private final OwnerVerificationService ownerVerificationService;
    private final String mailFrom;
    private final boolean exposeVerificationCodes;

    public CustomerRegistrationService(
            CustomerRegistrationRepository registrationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SmartParkMailDeliveryService mailDeliveryService,
            JwtService jwtService,
            OwnerVerificationService ownerVerificationService,
            @Value("${smartpark.mail.from:noreply@smartpark.local}") String mailFrom,
            @Value("${smartpark.auth.expose-verification-codes:false}")
                boolean exposeVerificationCodes) {
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailDeliveryService = mailDeliveryService;
        this.jwtService = jwtService;
        this.ownerVerificationService = ownerVerificationService;
        this.mailFrom = mailFrom;
        this.exposeVerificationCodes = exposeVerificationCodes;
    }

    @Transactional
    public PendingRegistration start(
            String fullName, String email, String phone, String password) {
        return startWithRole(fullName, email, phone, password, "USER");
    }

    @Transactional
    public PendingRegistration startOwner(
            String fullName, String email, String phone, String password) {
        return startWithRole(fullName, email, phone, password, "PARKING_OWNER");
    }

    private PendingRegistration startWithRole(
            String fullName, String email, String phone, String password, String role) {
        PasswordPolicy.validate(password);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Email is already registered"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        CustomerRegistration registration = registrationRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElse(null);
        if (registration != null && !registration.getRole().equals(role)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A pending registration already exists for this email with a different account type"
            );
        }
        if (registration != null
                && registration.getOtpSentAt().plusSeconds(60).isAfter(now)
                && !exposeVerificationCodes) {
            return new PendingRegistration(normalizedEmail, 600, null);
        }

        String encodedPassword = passwordEncoder.encode(password);
        if (registration == null) {
            registration = new CustomerRegistration(
                normalizedEmail, role, fullName.trim(),
                phone == null || phone.isBlank() ? null : phone.trim(),
                encodedPassword
            );
        } else {
            registration.updateRegistration(
                role,
                fullName.trim(),
                phone == null || phone.isBlank() ? null : phone.trim(),
                encodedPassword
            );
        }
        String developmentOtp = sendCode(registration, now);
        registrationRepository.save(registration);
        return new PendingRegistration(normalizedEmail, 600, developmentOtp);
    }

    @Transactional
    public PendingRegistration resend(String email) {
        String normalizedEmail = normalizeEmail(email);
        CustomerRegistration registration = registrationRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No pending registration was found for this email"
            ));
        LocalDateTime now = LocalDateTime.now();
        if (registration.getOtpSentAt().plusSeconds(60).isAfter(now)
                && !exposeVerificationCodes) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Please wait one minute before requesting another verification code"
            );
        }
        String developmentOtp = sendCode(registration, now);
        return new PendingRegistration(normalizedEmail, 600, developmentOtp);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthService.AuthResult verify(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        CustomerRegistration registration = registrationRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No pending registration was found for this email"
            ));
        LocalDateTime now = LocalDateTime.now();
        if (!registration.getOtpExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Verification code has expired. Request a new code."
            );
        }
        if (registration.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect verification attempts. Request a new code."
            );
        }
        if (otp == null || !otp.matches("\\d{6}")
                || !passwordEncoder.matches(otp, registration.getOtpHash())) {
            registration.recordFailedAttempt();
            registrationRepository.save(registration);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The verification code is incorrect"
            );
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            registrationRepository.delete(registration);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Email is already registered"
            );
        }

        User user = new User();
        user.setFullName(registration.getFullName());
        user.setEmail(registration.getEmail());
        user.setPhone(registration.getPhone());
        user.setPasswordHash(registration.getPasswordHash());
        user.setRole(registration.getRole());
        user = userRepository.saveAndFlush(user);
        if ("PARKING_OWNER".equals(registration.getRole())) {
            ownerVerificationService.initializeEmailVerifiedOwner(user);
        }
        registrationRepository.delete(registration);
        return new AuthService.AuthResult(
            user, jwtService.issueToken(user), jwtService.getExpiration().toSeconds()
        );
    }

    private String sendCode(CustomerRegistration registration, LocalDateTime now) {
        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        registration.setOtp(passwordEncoder.encode(otp), now);

        if (!exposeVerificationCodes) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(registration.getEmail());
            message.setSubject("Your SmartPark "
                + ("PARKING_OWNER".equals(registration.getRole()) ? "owner" : "customer")
                + " verification code");
            message.setText(
                "Your SmartPark verification code is " + otp
                    + ". It expires in 10 minutes. If you did not request this, ignore this email."
            );
            try {
                mailDeliveryService.send(message);
            } catch (MailException exception) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification email could not be sent. Check the email service configuration and try again.",
                    exception
                );
            }
        }
        return exposeVerificationCodes ? otp : null;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record PendingRegistration(
        String email,
        int expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String developmentOtp
    ) {
    }
}
