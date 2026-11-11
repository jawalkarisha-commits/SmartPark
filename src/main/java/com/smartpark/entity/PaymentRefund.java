package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refunds", uniqueConstraints = {
    @UniqueConstraint(name = "uk_refund_idempotency", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "uk_provider_refund_id", columnNames = "provider_refund_id")
})
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "provider_refund_id", length = 64)
    private String providerRefundId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PaymentRefund() {
    }

    public PaymentRefund(Payment payment, String idempotencyKey, long amountPaise) {
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.amountPaise = amountPaise;
        this.status = "PENDING";
    }

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProviderRefundId() { return providerRefundId; }
    public long getAmountPaise() { return amountPaise; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void markSubmitted(String providerRefundId) {
        this.providerRefundId = providerRefundId;
        this.status = "PROCESSING";
        this.failureReason = null;
    }

    public void markProcessed() {
        this.status = "COMPLETED";
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason == null ? "Provider refund failed" : reason.substring(0, Math.min(1000, reason.length()));
    }
}
