package com.smartpark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "bookings",
    indexes = {
        @Index(
            name = "idx_booking_slot_time_status",
            columnList = "parking_slot_id,status,start_time,end_time"
        ),
        @Index(
            name = "idx_booking_user_start_time",
            columnList = "user_id,start_time"
        )
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_booking_reference",
        columnNames = "booking_reference"
    )
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 36)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_slot_id", nullable = false)
    private ParkingSlot parkingSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_group_id")
    private BookingGroup bookingGroup;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 16)
    private BillingPeriod billingPeriod = BillingPeriod.HOURLY;

    @Column(name = "billed_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal billedRate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "actual_entry_time")
    private LocalDateTime actualEntryTime;

    @Column(name = "actual_exit_time")
    private LocalDateTime actualExitTime;

    @Column(name = "late_hours", nullable = false)
    private int lateHours;

    @Column(name = "late_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    protected Booking() {
    }

    public Booking(
            User user,
            Vehicle vehicle,
            ParkingSlot parkingSlot,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal hourlyRate,
            BigDecimal amount) {
        this.user = user;
        this.vehicle = vehicle;
        this.parkingSlot = parkingSlot;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.CONFIRMED;
        this.hourlyRate = hourlyRate;
        this.amount = amount;
        this.billingPeriod = BillingPeriod.HOURLY;
        this.billedRate = hourlyRate;
    }

    public Booking(
            User user,
            Vehicle vehicle,
            ParkingSlot parkingSlot,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BillingPeriod billingPeriod,
            BigDecimal hourlyRate,
            BigDecimal billedRate,
            BigDecimal amount) {
        this(user, vehicle, parkingSlot, startTime, endTime, hourlyRate, amount);
        this.billingPeriod = billingPeriod;
        this.billedRate = billedRate;
    }

    @PrePersist
    void initializePersistentFields() {
        if (bookingReference == null) {
            bookingReference = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSlot getParkingSlot() {
        return parkingSlot;
    }

    public BookingGroup getBookingGroup() { return bookingGroup; }
    public void assignToGroup(BookingGroup bookingGroup) { this.bookingGroup = bookingGroup; }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BillingPeriod getBillingPeriod() { return billingPeriod; }
    public BigDecimal getBilledRate() { return billedRate; }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public LocalDateTime getActualEntryTime() { return actualEntryTime; }
    public LocalDateTime getActualExitTime() { return actualExitTime; }
    public int getLateHours() { return lateHours; }
    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }

    public boolean isHoldExpired(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT
            && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }

    public void beginPaymentHold(LocalDateTime now) {
        this.status = BookingStatus.PENDING_PAYMENT;
        this.holdExpiresAt = now.plusMinutes(5);
    }

    public void confirmPayment(LocalDateTime now) {
        if (status != BookingStatus.PENDING_PAYMENT || isHoldExpired(now)) {
            throw new IllegalStateException("Payment hold is no longer active");
        }
        status = BookingStatus.CONFIRMED;
        holdExpiresAt = null;
    }

    public void expirePaymentHold(LocalDateTime now) {
        if (isHoldExpired(now)) {
            status = BookingStatus.EXPIRED;
        }
    }

    public void start(LocalDateTime now) {
        this.status = BookingStatus.ACTIVE;
        this.actualEntryTime = now;
    }

    public void markOverdue() {
        if (status == BookingStatus.ACTIVE && LocalDateTime.now().isAfter(endTime)) {
            status = BookingStatus.OVERDUE;
        }
    }

    public void complete(LocalDateTime now) {
        if (status != BookingStatus.ACTIVE && status != BookingStatus.OVERDUE) {
            throw new IllegalStateException("Only an active parking session can be completed");
        }
        long lateMinutes = Math.max(0, java.time.Duration.between(endTime, now).toMinutes());
        this.lateHours = (int) ((lateMinutes + 59) / 60);
        this.lateFeeAmount = hourlyRate
            .multiply(BigDecimal.valueOf(lateMinutes))
            .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        this.actualExitTime = now;
        this.status = BookingStatus.COMPLETED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
}
