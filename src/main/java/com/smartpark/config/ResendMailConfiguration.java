package com.smartpark.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration(proxyBeanMethods = false)
public class ResendMailConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "smartpark.mail.resend", name = "api-key")
    JavaMailSender resendJavaMailSender(
            @Value("${smartpark.mail.resend.api-key}") String apiKey) {
        return new ResendJavaMailSender(apiKey);
    }

    private static final class ResendJavaMailSender extends JavaMailSenderImpl {
        private final RestClient restClient;

        private ResendJavaMailSender(String apiKey) {
            this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        }

        @Override
        public void send(SimpleMailMessage message) {
            String[] recipients = message.getTo();
            if (recipients == null || recipients.length == 0) {
                throw new MailSendException("Email requires at least one recipient");
            }

            List<String> to = Arrays.stream(recipients).distinct().toList();
            try {
                restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmailRequest(
                        message.getFrom(), to, message.getSubject(), message.getText()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            } catch (RestClientException exception) {
                throw new MailSendException("Resend email delivery failed", exception);
            }
        }
    }

    private record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String text
    ) {
    }
}
