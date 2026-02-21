package com.ngxbot.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final EodhdProperties eodhdProperties;
    private final NotificationProperties notificationProperties;
    private final AiProperties aiProperties;

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

    @Bean
    public WebClient claudeWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        aiProperties.getTimeoutSeconds() * 1000)
                .responseTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()));

        return WebClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(1024 * 1024)) // 1 MB buffer
                .build();
    }
}
