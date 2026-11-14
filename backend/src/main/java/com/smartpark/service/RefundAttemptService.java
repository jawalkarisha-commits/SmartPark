package com.smartpark.service;

import com.smartpark.entity.Payment;
import com.smartpark.entity.PaymentRefund;
import com.smartpark.repository.PaymentRefundRepository;
import com.smartpark.repository.PaymentRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundAttemptService {

    private final PaymentRefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    public RefundAttemptService(
            PaymentRefundRepository refundRepository,
            PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefund prepare(Long paymentId, String idempotencyKey, long amountPaise) {
        Optional<PaymentRefund> existing = refundRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalStateException("Payment record was not found"));
        return refundRepository.save(new PaymentRefund(payment, idempotencyKey, amountPaise));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefund markSubmitted(Long refundId, String providerRefundId) {
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new IllegalStateException("Refund record was not found"));
        refund.markSubmitted(providerRefundId);
        return refund;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long refundId, String reason) {
        refundRepository.findById(refundId).ifPresent(refund -> refund.markFailed(reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String providerRefundId) {
        refundRepository.findByProviderRefundId(providerRefundId).ifPresent(refund -> {
            refund.markProcessed();
            refund.getPayment().markRefunded(providerRefundId, refund.getAmountPaise());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProviderFailure(String providerRefundId, String reason) {
        refundRepository.findByProviderRefundId(providerRefundId).ifPresent(refund -> {
            refund.markFailed(reason);
            refund.getPayment().markRefundFailed();
        });
    }

    @Transactional(readOnly = true)
    public List<RefundStatusView> getRecentRefunds() {
        return refundRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(refund -> {
                Payment payment = refund.getPayment();
                return new RefundStatusView(
                    refund.getId(),
                    payment.getBooking() == null ? null : payment.getBooking().getBookingReference(),
                    payment.getBookingGroup() == null
                        ? null : payment.getBookingGroup().getGroupReference(),
                    refund.getAmountPaise(),
                    refund.getStatus(),
                    refund.getProviderRefundId(),
                    refund.getFailureReason(),
                    refund.getCreatedAt()
                );
            })
            .toList();
    }

    public record RefundStatusView(
        Long id,
        String bookingReference,
        String groupReference,
        long amountPaise,
        String status,
        String providerRefundId,
        String failureReason,
        java.time.LocalDateTime createdAt
    ) {
    }
}
