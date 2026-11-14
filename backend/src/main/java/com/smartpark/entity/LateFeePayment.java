package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "late_fee_payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_late_fee_booking", columnNames = "booking_id"),
    @UniqueConstraint(name = "uk_late_fee_order", columnNames = "razorpay_order_id")
})
public class LateFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "razorpay_order_id", nullable = false, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LateFeePayment() {
    }

    public LateFeePayment(Booking booking, long amountPaise, String razorpayOrderId) {
        this.booking = booking;
        this.amountPaise = amountPaise;
        this.razorpayOrderId = razorpayOrderId;
    }

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() { updatedAt = LocalDateTime.now(); }

    public Booking getBooking() { return booking; }
    public long getAmountPaise() { return amountPaise; }
    public String getStatus() { return status; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void markPaid(String paymentId) {
        status = "PAID";
        razorpayPaymentId = paymentId;
    }
}
