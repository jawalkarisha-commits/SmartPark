package com.smartpark.service;

import com.smartpark.entity.PasswordResetToken;
import com.smartpark.entity.User;
import com.smartpark.repository.PasswordResetTokenRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.PasswordPolicy;
import com.smartpark.security.PasswordUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartpark.mail.from}")
    private String fromAddress;

    @Value("${smartpark.frontend.reset-url}")
    private String resetUrl;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            JavaMailSender mailSender,
            PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void sendResetLink(String email) {
        userRepository.findByEmail(email).ifPresent(user -> createAndSendToken(user));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordPolicy.validate(newPassword);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hashToken(token))
            .orElseThrow(PasswordResetService::invalidToken);

        if (resetToken.isUsed() || !resetToken.getExpiresAt().isAfter(Instant.now())) {
            throw invalidToken();
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markUsed();
    }

    private void createAndSendToken(User user) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tokenBytes);
        tokenRepository.deleteAllByUser_Id(user.getId());
        tokenRepository.save(new PasswordResetToken(
            user, hashToken(rawToken), Instant.now().plus(TOKEN_LIFETIME)
        ));

        String link = UriComponentsBuilder.fromUriString(resetUrl)
            .queryParam("resetToken", rawToken)
            .build()
            .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Reset your SmartPark password");
        message.setText(
            "Use this link within 30 minutes to reset your SmartPark password:\n\n"
                + link
                + "\n\nIf you did not request this, you can ignore this email."
        );

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            LOGGER.error("Unable to deliver a SmartPark password reset email", exception);
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Password reset email could not be sent. Check the email service configuration."
            );
        }
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired"
        );
    }
}
