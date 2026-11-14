package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "customer_registrations",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_customer_registration_email", columnNames = "email"
    )
)
public class CustomerRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "otp_expires_at", nullable = false)
    private LocalDateTime otpExpiresAt;

    @Column(name = "otp_sent_at", nullable = false)
    private LocalDateTime otpSentAt;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CustomerRegistration() {
    }

    public CustomerRegistration(
            String email, String role, String fullName, String phone, String passwordHash) {
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getOtpHash() { return otpHash; }
    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public LocalDateTime getOtpSentAt() { return otpSentAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updateRegistration(
            String role, String fullName, String phone, String passwordHash) {
        this.role = role;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public void setOtp(String otpHash, LocalDateTime now) {
        this.otpHash = otpHash;
        this.otpSentAt = now;
        this.otpExpiresAt = now.plusMinutes(10);
        this.otpAttempts = 0;
    }

    public void recordFailedAttempt() {
        otpAttempts++;
    }
}
