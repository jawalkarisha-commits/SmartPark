package com.smartpark.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingGroup;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.LateFeePayment;
import com.smartpark.entity.Payment;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.BookingGroupRepository;
import com.smartpark.repository.PaymentRepository;
import com.smartpark.repository.LateFeePaymentRepository;
import com.smartpark.repository.PaymentRefundRepository;
import com.smartpark.entity.PaymentRefund;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RazorpayPaymentService {

    private static final String API_BASE = "https://api.razorpay.com/v1";
    private final BookingRepository bookingRepository;
    private final BookingGroupRepository bookingGroupRepository;
    private final PaymentRepository paymentRepository;
    private final LateFeePaymentRepository lateFeePaymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final RefundAttemptService refundAttemptService;
    private final JsonMapper objectMapper;
    private final HttpClient httpClient;
    private final String keyId;
    private final String keySecret;
    private final String mode;

    public RazorpayPaymentService(
            BookingRepository bookingRepository,
            BookingGroupRepository bookingGroupRepository,
            PaymentRepository paymentRepository,
            LateFeePaymentRepository lateFeePaymentRepository,
            PaymentRefundRepository paymentRefundRepository,
            RefundAttemptService refundAttemptService,
            @Value("${smartpark.razorpay.key-id:}") String keyId,
            @Value("${smartpark.razorpay.key-secret:}") String keySecret,
            @Value("${smartpark.razorpay.mode:test}") String mode) {
        this.bookingRepository = bookingRepository;
        this.bookingGroupRepository = bookingGroupRepository;
        this.paymentRepository = paymentRepository;
        this.lateFeePaymentRepository = lateFeePaymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.refundAttemptService = refundAttemptService;
        this.objectMapper = JsonMapper.builder().build();
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.mode = mode;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Transactional
    public PaymentOrder createOrder(Long bookingId, Long userId) {
        requireCredentials();
        Booking booking = bookingRepository.findByIdAndUser_Id(bookingId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
        if (booking.getStatus() != com.smartpark.entity.BookingStatus.PENDING_PAYMENT
                || !booking.getStartTime().isAfter(java.time.LocalDateTime.now())
                || booking.isHoldExpired(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "The payment hold expired; please make a new reservation"
            );
        }

        Payment existing = paymentRepository.findByBooking_Id(bookingId).orElse(null);
        if (existing != null) {
            if (!"PENDING".equals(existing.getStatus())) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking already has a completed payment"
                );
            }
            return orderView(existing);
        }

        long amountPaise = booking.getAmount()
            .multiply(BigDecimal.valueOf(100)).longValueExact();
        if (amountPaise < 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Razorpay payments must be at least INR 1.00"
            );
        }
        String requestBody = objectMapper.writeValueAsString(
            java.util.Map.of(
                "amount", amountPaise,
                "currency", "INR",
                "receipt", booking.getBookingReference()
            )
        );

        JsonNode response = send("POST", "/orders", requestBody);
        String orderId = requiredText(response, "id");
        Payment payment = paymentRepository.save(
            new Payment(booking, amountPaise, orderId)
        );
        return orderView(payment);
    }

    @Transactional
    public PaymentOrder createGroupOrder(Long groupId, Long userId) {
        requireCredentials();
        BookingGroup group = bookingGroupRepository.findByIdAndUser_Id(groupId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking group not found"
            ));
        var bookings = bookingRepository.findAllByBookingGroup_IdOrderByIdAsc(groupId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (bookings.isEmpty() || bookings.stream().anyMatch(booking ->
                booking.getStatus() != BookingStatus.PENDING_PAYMENT
                    || booking.isHoldExpired(now))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "The group payment hold expired; please make a new reservation"
            );
        }

        Payment existing = paymentRepository.findByBookingGroup_Id(groupId).orElse(null);
        if (existing != null) {
            if (!"PENDING".equals(existing.getStatus())) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking group already has a completed payment"
                );
            }
            return orderView(existing);
        }
        long amountPaise = bookings.stream()
            .map(Booking::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .multiply(BigDecimal.valueOf(100))
            .longValueExact();
        if (amountPaise < 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Razorpay payments must be at least INR 1.00"
            );
        }
        String requestBody = objectMapper.writeValueAsString(
            java.util.Map.of(
                "amount", amountPaise,
                "currency", "INR",
                "receipt", group.getGroupReference()
            )
        );
        Payment payment = paymentRepository.save(new Payment(
            group, amountPaise, requiredText(send("POST", "/orders", requestBody), "id")
        ));
        return orderView(payment);
    }

    @Transactional
    public PaymentOrder createLateFeeOrder(Long bookingId, Long userId) {
        requireCredentials();
        Booking booking = bookingRepository.findByIdAndUser_Id(bookingId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (booking.getStatus() != BookingStatus.COMPLETED
                || booking.getLateFeeAmount().signum() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "This booking has no payable late fee"
            );
        }
        LateFeePayment existing = lateFeePaymentRepository.findByBooking_Id(bookingId).orElse(null);
        if (existing != null) {
            if (!"PENDING".equals(existing.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Late fee is already paid");
            }
            return new PaymentOrder(
                existing.getRazorpayOrderId(), keyId, existing.getAmountPaise(),
                "INR", "late-" + booking.getBookingReference(), "test".equalsIgnoreCase(mode)
            );
        }
        long amountPaise = booking.getLateFeeAmount()
            .multiply(BigDecimal.valueOf(100)).longValueExact();
        if (amountPaise < 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Razorpay payments must be at least INR 1.00"
            );
        }
        String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
            "amount", amountPaise,
            "currency", "INR",
            "receipt", "late-" + booking.getBookingReference()
        ));
        String orderId = requiredText(send("POST", "/orders", requestBody), "id");
        LateFeePayment feePayment = lateFeePaymentRepository.save(
            new LateFeePayment(booking, amountPaise, orderId)
        );
        return new PaymentOrder(
            feePayment.getRazorpayOrderId(), keyId, amountPaise, "INR",
            "late-" + booking.getBookingReference(), "test".equalsIgnoreCase(mode)
        );
    }

    @Transactional
    public LateFeePaymentResult verifyLateFeePayment(
            Long userId, String orderId, String paymentId, String signature) {
        requireCredentials();
        LateFeePayment feePayment = lateFeePaymentRepository.findByRazorpayOrderId(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Late-fee order not found"));
        if (!feePayment.getBooking().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Late-fee order not found");
        }
        if ("PAID".equals(feePayment.getStatus())) {
            return new LateFeePaymentResult(feePayment.getBooking().getId(), "PAID");
        }
        if (!MessageDigest.isEqual(
                expectedSignature(orderId, paymentId).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment signature is invalid");
        }
        JsonNode detail = send("GET", "/payments/" + pathPart(paymentId), null);
        String status = requiredText(detail, "status");
        if ("authorized".equals(status)) {
            send("POST", "/payments/" + pathPart(paymentId) + "/capture",
                "{\"amount\":" + feePayment.getAmountPaise() + ",\"currency\":\"INR\"}");
            detail = send("GET", "/payments/" + pathPart(paymentId), null);
            status = requiredText(detail, "status");
        }
        if (!"captured".equals(status)
                || !orderId.equals(requiredText(detail, "order_id"))
                || detail.path("amount").asLong(-1) != feePayment.getAmountPaise()
                || !"INR".equals(requiredText(detail, "currency"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Late-fee payment is not captured for this order");
        }
        feePayment.markPaid(paymentId);
        return new LateFeePaymentResult(feePayment.getBooking().getId(), "PAID");
    }

    @Transactional
    public void reconcileCapturedPayment(
            String orderId, String paymentId, long amountPaise, String currency) {
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
        if (payment == null) {
            LateFeePayment lateFee = lateFeePaymentRepository.findByRazorpayOrderId(orderId).orElse(null);
            if (lateFee == null || lateFee.getAmountPaise() != amountPaise
                    || !"INR".equals(currency)) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Captured payment order could not be reconciled"
                );
            }
            if (!"PAID".equals(lateFee.getStatus())) lateFee.markPaid(paymentId);
            return;
        }
        if (payment.getAmountPaise() != amountPaise || !"INR".equals(currency)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Captured payment amount or currency does not match the stored order"
            );
        }
        if ("PAID".equals(payment.getStatus())
                || "REFUND_PENDING".equals(payment.getStatus())
                || "REFUNDED".equals(payment.getStatus())
                || "PARTIALLY_REFUNDED".equals(payment.getStatus())) return;
        java.util.List<Booking> related = payment.getBookingGroup() == null
            ? java.util.List.of(payment.getBooking())
            : bookingRepository.findAllByBookingGroup_IdOrderByIdAsc(
                payment.getBookingGroup().getId()
            );
        java.util.List<Booking> locked = related.stream()
            .map(booking -> bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found"
                )))
            .toList();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean holdValid = !locked.isEmpty() && locked.stream().allMatch(booking ->
            booking.getStatus() == BookingStatus.PENDING_PAYMENT && !booking.isHoldExpired(now)
        );
        payment.markPaid(paymentId);
        if (holdValid) {
            locked.forEach(booking -> booking.confirmPayment(now));
        } else {
            locked.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                .forEach(booking -> booking.expirePaymentHold(now));
            issueRefund(payment, BigDecimal.valueOf(100), "expired-hold-" + payment.getId());
        }
    }

    @Transactional(readOnly = true)
    public String lateFeePaymentStatus(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUser_Id(bookingId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return lateFeePaymentRepository.findByBooking_Id(booking.getId())
            .map(LateFeePayment::getStatus)
            .orElse("UNPAID");
    }

    @Transactional
    public PaymentResult verifyPayment(
            Long userId, String orderId, String paymentId, String signature) {
        requireCredentials();
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment order not found"
            ));
        Long paymentUserId = payment.getBookingGroup() == null
            ? payment.getBooking().getUser().getId()
            : payment.getBookingGroup().getUser().getId();
        if (!paymentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found");
        }
        if ("PAID".equals(payment.getStatus())) {
            return new PaymentResult(
                payment.getBooking() == null ? null : payment.getBooking().getId(),
                payment.getBookingGroup() == null ? null : payment.getBookingGroup().getId(),
                payment.getStatus()
            );
        }

        java.util.List<Booking> lockedBookings;
        if (payment.getBookingGroup() == null) {
            lockedBookings = java.util.List.of(bookingRepository.findByIdForUpdate(
                    payment.getBooking().getId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found"
                )));
        } else {
            lockedBookings = bookingRepository.findAllByBookingGroup_IdOrderByIdAsc(
                    payment.getBookingGroup().getId())
                .stream()
                .map(booking -> bookingRepository.findByIdForUpdate(booking.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"
                    )))
                .toList();
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (lockedBookings.isEmpty() || lockedBookings.stream().anyMatch(booking ->
                booking.getStatus() != BookingStatus.PENDING_PAYMENT
                    || booking.isHoldExpired(now))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "The payment hold expired; the payment was not confirmed"
            );
        }

        if (!"PENDING".equals(payment.getStatus())
                || !MessageDigest.isEqual(
                    expectedSignature(orderId, paymentId).getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Razorpay payment signature is invalid"
            );
        }

        JsonNode detail = send("GET", "/payments/" + pathPart(paymentId), null);
        String status = requiredText(detail, "status");
        if ("authorized".equals(status)) {
            send("POST", "/payments/" + pathPart(paymentId) + "/capture",
                "{\"amount\":" + payment.getAmountPaise() + ",\"currency\":\"INR\"}");
            detail = send("GET", "/payments/" + pathPart(paymentId), null);
            status = requiredText(detail, "status");
        }
        if (!"captured".equals(status)
                || !orderId.equals(requiredText(detail, "order_id"))
                || detail.path("amount").asLong(-1) != payment.getAmountPaise()
                || !"INR".equals(requiredText(detail, "currency"))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Razorpay reports a payment that is not captured for this order"
            );
        }
        lockedBookings.forEach(booking -> booking.confirmPayment(now));
        payment.markPaid(paymentId);
        return new PaymentResult(
            payment.getBooking() == null ? null : payment.getBooking().getId(),
            payment.getBookingGroup() == null ? null : payment.getBookingGroup().getId(),
            payment.getStatus()
        );
    }

    @Transactional
    public void refundForCancellation(Long bookingId, BigDecimal refundPercentage) {
        Payment payment = paymentRepository.findByBooking_Id(bookingId).orElse(null);
        if (payment == null || !("PAID".equals(payment.getStatus())
                || "REFUND_FAILED".equals(payment.getStatus()))) return;
        issueRefund(payment, refundPercentage, "cancel-" + payment.getBooking().getBookingReference());
    }

    @Transactional
    public void refundGroupForCancellation(Long groupId, BigDecimal refundPercentage) {
        Payment payment = paymentRepository.findByBookingGroup_Id(groupId).orElse(null);
        if (payment == null || !("PAID".equals(payment.getStatus())
                || "REFUND_FAILED".equals(payment.getStatus()))) return;
        issueRefund(payment, refundPercentage, "cancel-group-" + groupId);
    }

    @Transactional
    public void retryRefund(Long refundId) {
        PaymentRefund attempt = paymentRefundRepository.findById(refundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
        if (!"FAILED".equals(attempt.getStatus()) && !"PENDING".equals(attempt.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only pending or failed refunds can be retried"
            );
        }
        Payment payment = attempt.getPayment();
        if (payment.getRazorpayPaymentId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment provider ID is unavailable");
        }
        requireCredentials();
        payment.markRefundPending();
        String requestBody = "{\"amount\":" + attempt.getAmountPaise()
            + ",\"receipt\":\"" + attempt.getIdempotencyKey()
            + "\",\"notes\":{\"reason\":\"booking_cancellation\"}}";
        try {
            JsonNode response = send(
                "POST",
                "/payments/" + pathPart(payment.getRazorpayPaymentId()) + "/refund",
                requestBody,
                attempt.getIdempotencyKey()
            );
            String refundIdFromProvider = requiredText(response, "id");
            refundAttemptService.markSubmitted(attempt.getId(), refundIdFromProvider);
            if ("processed".equals(requiredText(response, "status"))) {
                refundAttemptService.markProcessed(refundIdFromProvider);
                payment.markRefunded(refundIdFromProvider, attempt.getAmountPaise());
            }
        } catch (ResponseStatusException exception) {
            refundAttemptService.markFailed(attempt.getId(), exception.getReason());
            payment.markRefundFailed();
            throw exception;
        }
    }

    private void issueRefund(Payment payment, BigDecimal refundPercentage, String receipt) {
        requireCredentials();
        if (payment.getRazorpayPaymentId() == null) {
            throw new IllegalStateException("Paid payment is missing its provider payment ID");
        }
        BigDecimal percentage = refundPercentage.divide(
            BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP
        );
        long refundPaise = BigDecimal.valueOf(payment.getAmountPaise())
            .multiply(percentage)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
        if (refundPaise == 0) {
            payment.markNoRefund();
            return;
        }
        PaymentRefund attempt = refundAttemptService.prepare(
            payment.getId(), receipt, refundPaise
        );
        if ("COMPLETED".equals(attempt.getStatus())
                || "PROCESSING".equals(attempt.getStatus())) {
            payment.markRefundPending();
            return;
        }
        payment.markRefundPending();
        String requestBody = "{\"amount\":" + refundPaise
            + ",\"receipt\":\"" + receipt
            + "\",\"notes\":{\"reason\":\"booking_cancellation\"}}";
        try {
            JsonNode response = send(
                "POST",
                "/payments/" + pathPart(payment.getRazorpayPaymentId()) + "/refund",
                requestBody,
                attempt.getIdempotencyKey()
            );
            String refundId = requiredText(response, "id");
            refundAttemptService.markSubmitted(attempt.getId(), refundId);
            if ("processed".equals(requiredText(response, "status"))) {
                refundAttemptService.markProcessed(refundId);
                payment.markRefunded(refundId, refundPaise);
            }
        } catch (ResponseStatusException exception) {
            refundAttemptService.markFailed(attempt.getId(), exception.getReason());
            payment.markRefundFailed();
            throw exception;
        }
    }

    private JsonNode send(String method, String path, String body) {
        return send(method, path, body, null);
    }

    private JsonNode send(String method, String path, String body, String idempotencyKey) {
        String authorization = Base64.getEncoder().encodeToString(
            (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + path))
            .header("Authorization", "Basic " + authorization)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            request.header("X-Razorpay-Idempotency-Key", idempotencyKey);
        }
        if ("GET".equals(method)) {
            request.GET();
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        try {
            HttpResponse<String> response = httpClient.send(
                request.build(), HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Razorpay request failed with HTTP " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Razorpay could not be reached", exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Razorpay request was interrupted", exception
            );
        }
    }

    private String expectedSignature(String orderId, String paymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private void requireCredentials() {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Razorpay is not configured; set SMARTPARK_RAZORPAY_KEY_ID and SMARTPARK_RAZORPAY_KEY_SECRET"
            );
        }
        if (!"test".equalsIgnoreCase(mode) && !"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("SMARTPARK_RAZORPAY_MODE must be test or live");
        }
        if ("live".equalsIgnoreCase(mode) && keyId.startsWith("rzp_test_")) {
            throw new IllegalStateException("Live Razorpay mode cannot use a test key");
        }
        if ("test".equalsIgnoreCase(mode) && !keyId.startsWith("rzp_test_")) {
            throw new IllegalStateException("Test Razorpay mode requires an rzp_test_ key");
        }
    }

    private PaymentOrder orderView(Payment payment) {
        String receipt = payment.getBookingGroup() == null
            ? payment.getBooking().getBookingReference()
            : payment.getBookingGroup().getGroupReference();
        return new PaymentOrder(
            payment.getRazorpayOrderId(), keyId, payment.getAmountPaise(),
            payment.getCurrency(), receipt,
            "test".equalsIgnoreCase(mode)
        );
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Razorpay returned an incomplete response"
            );
        }
        return value.asText();
    }

    private static String pathPart(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay identifier");
        }
        return value;
    }

    public record PaymentOrder(
        String orderId,
        String keyId,
        long amountPaise,
        String currency,
        String receipt,
        boolean testMode
    ) {
    }

    public record PaymentResult(Long bookingId, Long bookingGroupId, String paymentStatus) {
    }

    public record LateFeePaymentResult(Long bookingId, String paymentStatus) {
    }
}
