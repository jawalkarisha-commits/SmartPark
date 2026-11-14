package com.smartpark.repository;

import com.smartpark.entity.LateFeePayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LateFeePaymentRepository extends JpaRepository<LateFeePayment, Long> {
    Optional<LateFeePayment> findByBooking_Id(Long bookingId);
    Optional<LateFeePayment> findByRazorpayOrderId(String orderId);
}
