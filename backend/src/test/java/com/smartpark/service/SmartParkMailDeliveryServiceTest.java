package com.smartpark.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SmartParkMailDeliveryServiceTest {

    private static final String BREVO_SEND_URL =
        "https://api.brevo.com/v3/smtp/email";

    @Test
    void sendsTransactionalMessageThroughBrevoHttpsApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JavaMailSender smtpMailSender = mock(JavaMailSender.class);
        SmartParkMailDeliveryService service = new SmartParkMailDeliveryService(
            smtpMailSender, builder.build(), "brevo", "test-api-key", "SmartPark"
        );

        server.expect(requestTo(BREVO_SEND_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", "test-api-key"))
            .andExpect(content().json("""
                {
                  "sender": {"name": "SmartPark", "email": "sender@example.com"},
                  "to": [{"email": "owner@example.com"}],
                  "subject": "Verification code",
                  "textContent": "Your code is 123456"
                }
                """))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.send(message());

        server.verify();
    }

    @Test
    void delegatesToSmtpWhenConfigured() {
        JavaMailSender smtpMailSender = mock(JavaMailSender.class);
        SimpleMailMessage message = message();
        SmartParkMailDeliveryService service = new SmartParkMailDeliveryService(
            smtpMailSender, RestClient.create(), "smtp", "", "SmartPark"
        );

        service.send(message);

        verify(smtpMailSender).send(message);
    }

    @Test
    void failsClearlyWhenBrevoApiKeyIsMissing() {
        SmartParkMailDeliveryService service = new SmartParkMailDeliveryService(
            mock(JavaMailSender.class), RestClient.create(), "brevo", "", "SmartPark"
        );

        assertThrows(MailSendException.class, () -> service.send(message()));
    }

    private static SimpleMailMessage message() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sender@example.com");
        message.setTo("owner@example.com");
        message.setSubject("Verification code");
        message.setText("Your code is 123456");
        return message;
    }
}
