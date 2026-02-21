package com.ngxbot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final EodhdProperties eodhdProperties;
    private final NotificationProperties notificationProperties;

    @Bean
    public WebClient eodhdWebClient() {
        return WebClient.builder()
                .baseUrl(eodhdProperties.getBaseUrl())
                .build();
    }

    @Bean
    public WebClient wahaWebClient() {
        return WebClient.builder()
                .baseUrl(notificationProperties.getWhatsapp().getWahaBaseUrl())
                .build();
    }

    @Bean
    public WebClient telegramWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }
}
