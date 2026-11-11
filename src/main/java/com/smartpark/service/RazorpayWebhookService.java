package com.smartpark.service;

import com.smartpark.entity.PaymentWebhookEvent;
import com.smartpark.repository.PaymentRefundRepository;
import com.smartpark.repository.PaymentWebhookEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RazorpayWebhookService {

    private final String webhookSecret;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final PaymentWebhookEventRepository eventRepository;
    private final PaymentRefundRepository refundRepository;
    private final RefundAttemptService refundAttemptService;
    private final RazorpayPaymentService paymentService;

    public RazorpayWebhookService(
            @Value("${smartpark.razorpay.webhook-secret:}") String webhookSecret,
            PaymentWebhookEventRepository eventRepository,
            PaymentRefundRepository refundRepository,
            RefundAttemptService refundAttemptService,
            RazorpayPaymentService paymentService) {
        this.webhookSecret = webhookSecret;
        this.eventRepository = eventRepository;
        this.refundRepository = refundRepository;
        this.refundAttemptService = refundAttemptService;
        this.paymentService = paymentService;
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public void receive(String eventId, String signature, String payload) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Razorpay webhook verification is not configured"
            );
        }
        if (eventId == null || !eventId.matches("[A-Za-z0-9._:-]{1,100}")
                || payload == null || payload.length() > 1_000_000
                || signature == null || !signature.matches("[a-fA-F0-9]{64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook request");
        }
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.US_ASCII),
                hmac(payload).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Razorpay webhook signature is invalid");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(payload);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload is not valid JSON", exception);
        }
        String eventType = root.path("event").asText("");
        if (eventType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook event type is missing");
        }
        PaymentWebhookEvent event = eventRepository.findByProviderEventId(eventId)
            .orElseGet(() -> eventRepository.save(new PaymentWebhookEvent(
                eventId, eventType, sha256(payload)
            )));
        if (!event.getPayloadHash().equals(sha256(payload))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Razorpay event ID was reused with a different payload"
            );
        }
        if (event.isProcessed()) return;
        try {
            if ("refund.processed".equals(eventType) || "refund.failed".equals(eventType)) {
                String refundId = root.path("payload").path("refund")
                    .path("entity").path("id").asText("");
                if (refundId.isBlank()) {
                    throw new IllegalArgumentException("Refund webhook is missing its provider refund ID");
                }
                if (refundRepository.findByProviderRefundId(refundId).isEmpty()) {
                    throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Refund request has not been registered yet"
                    );
                }
                if ("refund.processed".equals(eventType)) {
                    refundAttemptService.markProcessed(refundId);
                } else {
                    refundAttemptService.markProviderFailure(refundId, "Provider reported refund failure");
                }
            } else if ("payment.captured".equals(eventType)) {
                JsonNode entity = root.path("payload").path("payment").path("entity");
                String orderId = entity.path("order_id").asText("");
                String paymentId = entity.path("id").asText("");
                long amount = entity.path("amount").asLong(-1);
                String currency = entity.path("currency").asText("");
                if (orderId.isBlank() || paymentId.isBlank() || amount < 0 || currency.isBlank()) {
                    throw new IllegalArgumentException("Captured-payment webhook is incomplete");
                }
                paymentService.reconcileCapturedPayment(orderId, paymentId, amount, currency);
            }
            event.markProcessed();
        } catch (RuntimeException exception) {
            event.markFailed(exception.getMessage());
            throw exception;
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
