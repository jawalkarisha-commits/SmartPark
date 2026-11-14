package com.smartpark.controller;

import com.smartpark.security.CurrentUser;
import com.smartpark.service.RazorpayPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('USER')")
public class PaymentController {

    private final RazorpayPaymentService paymentService;

    public PaymentController(RazorpayPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/bookings/{bookingId}/order")
    public RazorpayPaymentService.PaymentOrder createOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long bookingId) {
        return paymentService.createOrder(bookingId, currentUser.id());
    }

    @PostMapping("/groups/{groupId}/order")
    public RazorpayPaymentService.PaymentOrder createGroupOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long groupId) {
        return paymentService.createGroupOrder(groupId, currentUser.id());
    }

    @PostMapping("/bookings/{bookingId}/late-fee/order")
    public RazorpayPaymentService.PaymentOrder createLateFeeOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long bookingId) {
        return paymentService.createLateFeeOrder(bookingId, currentUser.id());
    }

    @GetMapping("/bookings/{bookingId}/late-fee/status")
    public LateFeeStatus lateFeeStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long bookingId) {
        return new LateFeeStatus(paymentService.lateFeePaymentStatus(bookingId, currentUser.id()));
    }

    @PostMapping("/late-fee/verify")
    public RazorpayPaymentService.LateFeePaymentResult verifyLateFee(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyLateFeePayment(
            currentUser.id(), request.orderId(), request.paymentId(), request.signature()
        );
    }

    @PostMapping("/verify")
    public RazorpayPaymentService.PaymentResult verify(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyPayment(
            currentUser.id(), request.orderId(), request.paymentId(), request.signature()
        );
    }

    public record VerifyPaymentRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String orderId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String paymentId,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String signature
    ) {
    }

    public record LateFeeStatus(String status) {
    }
}
