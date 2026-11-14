package com.smartpark.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SmartParkMailDeliveryService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SmartParkMailDeliveryService.class);
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final JavaMailSender smtpMailSender;
    private final RestClient restClient;
    private final String provider;
    private final String brevoApiKey;
    private final String fromName;

    @Autowired
    public SmartParkMailDeliveryService(
            JavaMailSender smtpMailSender,
            @Value("${smartpark.mail.provider:smtp}") String provider,
            @Value("${smartpark.mail.brevo-api-key:}") String brevoApiKey,
            @Value("${smartpark.mail.from-name:SmartPark}") String fromName) {
        this(
            smtpMailSender,
            createRestClient(),
            provider,
            brevoApiKey,
            fromName
        );
    }

    SmartParkMailDeliveryService(
            JavaMailSender smtpMailSender,
            RestClient restClient,
            String provider,
            String brevoApiKey,
            String fromName) {
        this.smtpMailSender = smtpMailSender;
        this.restClient = restClient;
        this.provider = provider.trim().toLowerCase(Locale.ROOT);
        this.brevoApiKey = brevoApiKey;
        this.fromName = fromName;
    }

    public void send(SimpleMailMessage message) {
        if ("smtp".equals(provider)) {
            smtpMailSender.send(message);
            return;
        }
        if (!"brevo".equals(provider)) {
            throw new MailSendException("Unsupported email delivery provider");
        }
        sendWithBrevo(message);
    }

    private void sendWithBrevo(SimpleMailMessage message) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new MailSendException("Brevo API key is not configured");
        }
        String from = message.getFrom();
        String[] recipients = message.getTo();
        if (from == null || from.isBlank() || recipients == null || recipients.length == 0) {
            throw new MailSendException("Email sender and at least one recipient are required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", from));
        List<Map<String, String>> to = new ArrayList<>();
        for (String recipient : recipients) {
            to.add(Map.of("email", recipient));
        }
        payload.put("to", to);
        payload.put("subject", message.getSubject());
        payload.put("textContent", message.getText());

        try {
            restClient.post()
                .uri(BREVO_SEND_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("api-key", brevoApiKey)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                "Brevo rejected an email request (HTTP status {})",
                exception.getStatusCode().value()
            );
            throw new MailSendException("Brevo email delivery failed", exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                "Brevo email request failed ({})",
                exception.getClass().getSimpleName()
            );
            throw new MailSendException("Brevo email delivery failed", exception);
        }
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
