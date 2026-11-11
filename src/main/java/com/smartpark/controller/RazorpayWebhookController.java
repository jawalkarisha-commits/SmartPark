package com.smartpark.controller;

import com.smartpark.service.RazorpayWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    public RazorpayWebhookController(RazorpayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(
            @RequestHeader("x-razorpay-event-id") String eventId,
            @RequestHeader("x-razorpay-signature") String signature,
            @RequestBody String payload) {
        webhookService.receive(eventId, signature, payload);
    }
}
