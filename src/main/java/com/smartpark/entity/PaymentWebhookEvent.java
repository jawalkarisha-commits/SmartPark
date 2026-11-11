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
@Table(name = "payment_webhook_events", uniqueConstraints = @UniqueConstraint(
    name = "uk_payment_webhook_event", columnNames = "provider_event_id"
))
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processing_error", length = 1000)
    private String processingError;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String providerEventId, String eventType, String payloadHash) {
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payloadHash = payloadHash;
    }

    @PrePersist
    void received() { receivedAt = LocalDateTime.now(); }

    public String getProviderEventId() { return providerEventId; }
    public String getEventType() { return eventType; }
    public String getPayloadHash() { return payloadHash; }
    public boolean isProcessed() { return processedAt != null; }
    public void markProcessed() { processedAt = LocalDateTime.now(); processingError = null; }
    public void markFailed(String reason) {
        processingError = reason == null ? "Webhook processing failed" : reason.substring(0, Math.min(1000, reason.length()));
    }
}
