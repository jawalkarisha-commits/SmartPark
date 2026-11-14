package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_group_id", unique = true)
    private BookingGroup bookingGroup;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "refund_amount_paise", nullable = false)
    private long refundAmountPaise;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "razorpay_refund_id", length = 64)
    private String razorpayRefundId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Payment() {
    }

    public Payment(Booking booking, long amountPaise, String razorpayOrderId) {
        this.booking = booking;
        this.amountPaise = amountPaise;
        this.razorpayOrderId = razorpayOrderId;
    }

    public Payment(BookingGroup bookingGroup, long amountPaise, String razorpayOrderId) {
        this.bookingGroup = bookingGroup;
        this.amountPaise = amountPaise;
        this.razorpayOrderId = razorpayOrderId;
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public BookingGroup getBookingGroup() { return bookingGroup; }
    public long getAmountPaise() { return amountPaise; }
    public long getRefundAmountPaise() { return refundAmountPaise; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRazorpayRefundId() { return razorpayRefundId; }

    public void markPaid(String paymentId) {
        status = "PAID";
        razorpayPaymentId = paymentId;
        updatedAt = LocalDateTime.now();
    }

    public void markRefunded(String refundId, long refundAmountPaise) {
        status = refundAmountPaise == amountPaise ? "REFUNDED" : "PARTIALLY_REFUNDED";
        razorpayRefundId = refundId;
        this.refundAmountPaise = refundAmountPaise;
        updatedAt = LocalDateTime.now();
    }

    public void markNoRefund() {
        status = "CANCELLED";
        updatedAt = LocalDateTime.now();
    }

    public void markRefundPending() {
        status = "REFUND_PENDING";
        updatedAt = LocalDateTime.now();
    }

    public void markRefundFailed() {
        status = "REFUND_FAILED";
        updatedAt = LocalDateTime.now();
    }
}
