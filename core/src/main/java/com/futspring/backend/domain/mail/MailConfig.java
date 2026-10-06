package com.futspring.backend.domain.mail;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Getter
public class MailConfig {

    private static final String RESEND_API_URL = "https://api.resend.com";

    @Value("${app.mail.resend-api-key}")
    private String resendApiKey;

    @Value("${app.mail.from}")
    private String from;

    // Base URL of the web app, used to build the links sent by e-mail
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public EmailSender emailSender(RestClient.Builder builder) {
        return resendApiKey.isBlank() ? new NoopEmailSender() : new ResendEmailSender(resendClient(builder), from);
    }

    // Timeouts so a slow Resend never holds an async thread forever
    private RestClient resendClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return builder
                .baseUrl(RESEND_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                .requestFactory(requestFactory)
                .build();
    }
}
