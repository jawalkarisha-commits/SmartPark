package com.smartpark.controller;

import com.smartpark.service.RazorpayPaymentService;
import com.smartpark.service.RefundAttemptService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundAttemptService refundAttemptService;
    private final RazorpayPaymentService paymentService;

    public AdminRefundController(
            RefundAttemptService refundAttemptService,
            RazorpayPaymentService paymentService) {
        this.refundAttemptService = refundAttemptService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<RefundAttemptService.RefundStatusView> getRecentRefunds() {
        return refundAttemptService.getRecentRefunds();
    }

    @PostMapping("/{refundId}/retry")
    public void retryRefund(@PathVariable Long refundId) {
        paymentService.retryRefund(refundId);
    }
}
