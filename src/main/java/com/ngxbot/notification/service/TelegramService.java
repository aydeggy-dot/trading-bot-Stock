package com.ngxbot.notification.service;

import com.ngxbot.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Sends messages via the Telegram Bot API.
 * <p>
 * POST /bot{token}/sendMessage — text messages with Markdown formatting.
 */
@Slf4j
@Service
public class TelegramService {

    private final NotificationProperties notificationProperties;
    private final WebClient telegramWebClient;

    public TelegramService(NotificationProperties notificationProperties,
                           WebClient.Builder webClientBuilder) {
        this.notificationProperties = notificationProperties;
        this.telegramWebClient = webClientBuilder
                .baseUrl("https://api.telegram.org")
                .build();
    }

    /**
     * Sends a Markdown-formatted message to the configured Telegram chat.
     *
     * @param message the text to send (supports Telegram Markdown)
     */
    public void sendMessage(String message) {
        NotificationProperties.Telegram tg = notificationProperties.getTelegram();

        if (!tg.isEnabled()) {
            log.debug("Telegram notifications disabled — skipping message");
            return;
        }

        String botToken = tg.getBotToken();
        String chatId = tg.getChatId();
        log.info("Sending Telegram message to chatId={}", chatId);

        try {
            Map<String, String> body = Map.of(
                    "chat_id", chatId,
                    "text", message,
                    "parse_mode", "Markdown"
            );

            telegramWebClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Telegram message sent successfully to chatId={}", chatId);

        } catch (WebClientResponseException e) {
            log.error("Telegram send failed — HTTP {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Telegram send failed: {}", e.getMessage(), e);
        }
    }
}
