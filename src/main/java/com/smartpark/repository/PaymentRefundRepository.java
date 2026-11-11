package com.smartpark.repository;

import com.smartpark.entity.PaymentRefund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);
    Optional<PaymentRefund> findById(Long id);
    List<PaymentRefund> findAllByOrderByCreatedAtDesc();
}
