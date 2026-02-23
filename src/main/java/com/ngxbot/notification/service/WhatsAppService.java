package com.ngxbot.notification.service;

import com.ngxbot.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Sends messages via WAHA (WhatsApp HTTP API).
 * <p>
 * POST /api/sendText  — plain text messages
 * POST /api/sendImage — image with caption (stubbed for now)
 */
@Slf4j
@Service
public class WhatsAppService {

    private final NotificationProperties notificationProperties;
    private final WebClient wahaWebClient;

    public WhatsAppService(NotificationProperties notificationProperties,
                           WebClient.Builder webClientBuilder) {
        this.notificationProperties = notificationProperties;
        NotificationProperties.WhatsApp wa = notificationProperties.getWhatsapp();
        WebClient.Builder builder = webClientBuilder
                .baseUrl(wa.getWahaBaseUrl());
        if (wa.getWahaApiKey() != null && !wa.getWahaApiKey().isBlank()) {
            builder.defaultHeader("X-Api-Key", wa.getWahaApiKey());
        }
        this.wahaWebClient = builder.build();
    }

    /**
     * Sends a plain-text WhatsApp message via WAHA.
     *
     * @param message the text to send
     */
    public void sendMessage(String message) {
        NotificationProperties.WhatsApp wa = notificationProperties.getWhatsapp();

        if (!wa.isEnabled()) {
            log.debug("WhatsApp notifications disabled — skipping message");
            return;
        }

        log.info("Sending WhatsApp message to chatId={}", wa.getChatId());

        try {
            Map<String, String> body = Map.of(
                    "chatId", wa.getChatId(),
                    "text", message,
                    "session", wa.getWahaSession()
            );

            wahaWebClient.post()
                    .uri("/api/sendText")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("WhatsApp message sent successfully to chatId={}", wa.getChatId());

        } catch (WebClientResponseException e) {
            log.error("WhatsApp send failed — HTTP {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("WhatsApp send failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends an image with caption via WAHA.
     * <p>
     * Currently stubbed — logs that image sending is not yet implemented.
     *
     * @param message   caption text
     * @param imageData raw image bytes
     * @param filename  the image filename (e.g. "chart.png")
     */
    public void sendMessageWithImage(String message, byte[] imageData, String filename) {
        NotificationProperties.WhatsApp wa = notificationProperties.getWhatsapp();

        if (!wa.isEnabled()) {
            log.debug("WhatsApp notifications disabled — skipping image message");
            return;
        }

        log.warn("WhatsApp image send not yet implemented — message='{}', filename='{}', imageSize={} bytes",
                message, filename, imageData != null ? imageData.length : 0);
    }
}
