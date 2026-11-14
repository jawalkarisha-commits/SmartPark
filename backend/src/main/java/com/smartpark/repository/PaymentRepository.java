package com.smartpark.repository;

import com.smartpark.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBooking_Id(Long bookingId);
    Optional<Payment> findByBookingGroup_Id(Long bookingGroupId);
    Optional<Payment> findByRazorpayOrderId(String orderId);
}
