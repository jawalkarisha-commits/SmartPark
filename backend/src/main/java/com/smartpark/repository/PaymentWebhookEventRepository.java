package com.smartpark.repository;

import com.smartpark.entity.PaymentWebhookEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderEventId(String providerEventId);
    Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId);
}
