package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "owner_verifications",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_owner_verification_user", columnNames = "owner_user_id"
    )
)
public class OwnerVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "otp_hash", length = 100)
    private String otpHash;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @Column(name = "otp_sent_at")
    private LocalDateTime otpSentAt;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "document_storage_key", length = 36)
    private String documentStorageKey;

    @Column(name = "document_file_name", length = 255)
    private String documentFileName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "document_size")
    private Long documentSize;

    @Column(name = "document_uploaded_at")
    private LocalDateTime documentUploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 16)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approval_method", length = 16)
    private String approvalMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OwnerVerification() {
    }

    public static OwnerVerification pending(User owner) {
        OwnerVerification verification = new OwnerVerification();
        verification.owner = owner;
        verification.createdAt = LocalDateTime.now();
        return verification;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getOtpHash() { return otpHash; }
    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public LocalDateTime getOtpSentAt() { return otpSentAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public String getDocumentStorageKey() { return documentStorageKey; }
    public String getDocumentFileName() { return documentFileName; }
    public String getDocumentContentType() { return documentContentType; }
    public Long getDocumentSize() { return documentSize; }
    public LocalDateTime getDocumentUploadedAt() { return documentUploadedAt; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public String getApprovalMethod() { return approvalMethod; }
    public User getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void prepareOtp(String otpHash, LocalDateTime now) {
        this.otpHash = otpHash;
        this.otpSentAt = now;
        this.otpExpiresAt = now.plusMinutes(10);
        this.otpAttempts = 0;
    }

    public void recordFailedOtpAttempt() {
        this.otpAttempts++;
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.otpHash = null;
        this.otpExpiresAt = null;
        this.otpSentAt = null;
        this.otpAttempts = 0;
    }

    public void setDocument(
            String storageKey, String fileName, String contentType,
            long size, LocalDateTime uploadedAt) {
        this.documentStorageKey = storageKey;
        this.documentFileName = fileName;
        this.documentContentType = contentType;
        this.documentSize = size;
        this.documentUploadedAt = uploadedAt;
    }

    public void approveManually(User admin, LocalDateTime now) {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.approvalMethod = "MANUAL";
        this.reviewedBy = admin;
        this.reviewedAt = now;
        this.rejectionReason = null;
    }

    public void rejectManually(User admin, String reason, LocalDateTime now) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.approvalMethod = "MANUAL";
        this.reviewedBy = admin;
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
