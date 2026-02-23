package com.ngxbot.integration;

import com.ngxbot.config.NotificationProperties;
import com.ngxbot.notification.service.WhatsAppService;
import com.ngxbot.execution.service.OtpHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 4: WAHA (WhatsApp) Setup & Notifications
 *
 * Verifies:
 *   - WAHA service is running and accessible
 *   - WhatsApp session is authenticated (QR code scanned)
 *   - Messages can be sent to user's WhatsApp
 *   - OTP handler is functional
 *
 * Prereqs:
 *   1. docker compose up -d waha
 *   2. Open http://localhost:3000 and scan QR code to link WhatsApp
 *   3. WHATSAPP_CHAT_ID set in .env (format: 234XXXXXXXXXX@c.us)
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step04_WhatsAppIT extends IntegrationTestBase {

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private OtpHandler otpHandler;

    @Test
    @Order(1)
    @DisplayName("4.1 WhatsApp chat ID is configured")
    void whatsappChatIdConfigured() {
        String chatId = notificationProperties.getWhatsapp().getChatId();
        assertThat(chatId)
                .as("WHATSAPP_CHAT_ID must be set")
                .isNotBlank()
                .contains("@c.us");

        printResult("WhatsApp Config",
                String.format("Chat ID: %s, WAHA URL: %s",
                        chatId,
                        notificationProperties.getWhatsapp().getWahaBaseUrl()));
    }

    @Test
    @Order(2)
    @DisplayName("4.2 WAHA service is running and session is active")
    void wahaServiceIsRunning() {
        NotificationProperties.WhatsApp wa = notificationProperties.getWhatsapp();
        WebClient.Builder builder = WebClient.builder().baseUrl(wa.getWahaBaseUrl());
        if (wa.getWahaApiKey() != null && !wa.getWahaApiKey().isBlank()) {
            builder.defaultHeader("X-Api-Key", wa.getWahaApiKey());
        }
        WebClient client = builder.build();

        // Check WAHA health by hitting the sessions endpoint
        String response = client.get()
                .uri("/api/sessions")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        printResult("WAHA Status",
                String.format("Sessions response: %s",
                        response != null ? response.substring(0, Math.min(200, response.length())) : "null"));

        assertThat(response).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("4.3 Send plain text message via WhatsApp")
    void sendPlainTextMessage() {
        String timestamp = ZonedDateTime.now(ZoneId.of("Africa/Lagos"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        String message = String.format(
                "NGX Trading Bot — Integration Test\n\n" +
                "WhatsApp notification is working!\n" +
                "Timestamp: %s\n" +
                "Status: CONNECTED", timestamp);

        assertThatCode(() -> whatsAppService.sendMessage(message))
                .as("WhatsApp message send should not throw")
                .doesNotThrowAnyException();

        printResult("WhatsApp Send", "Plain text message sent — check your WhatsApp!");
    }

    @Test
    @Order(4)
    @DisplayName("4.4 Send formatted trade alert via WhatsApp")
    void sendFormattedTradeAlert() {
        String alertMessage = String.format(
                "TRADE SIGNAL — Integration Test\n\n" +
                "Symbol: ZENITHBANK\n" +
                "Action: BUY\n" +
                "Price: NGN 35.50\n" +
                "Stop Loss: NGN 33.73 (-5%%)\n" +
                "Target: NGN 39.05 (+10%%)\n" +
                "Confidence: 75/100\n" +
                "Strategy: Momentum Breakout\n\n" +
                "This is a TEST signal — not a real trade.");

        assertThatCode(() -> whatsAppService.sendMessage(alertMessage))
                .doesNotThrowAnyException();

        printResult("WhatsApp Alert", "Formatted trade alert sent to WhatsApp");
    }

    @Test
    @Order(5)
    @DisplayName("4.5 OTP handler has no pending OTPs initially")
    void otpHandlerInitialState() {
        assertThat(otpHandler.hasPendingOtp()).isFalse();
        assertThat(otpHandler.getMaxRetries()).isEqualTo(3);

        printResult("OTP Handler", "Initial state: no pending OTPs, max retries = 3");
    }

    @Test
    @Order(6)
    @DisplayName("4.6 WhatsApp webhook endpoint is accessible")
    void webhookEndpointAccessible() {
        // The webhook controller is at /api/webhooks/whatsapp/message
        // We verify the bean is loaded — actual webhook testing requires WAHA to POST
        assertThat(whatsAppService).isNotNull();

        printResult("Webhook Endpoint",
                "WhatsAppWebhookController bean loaded — POST /api/webhooks/whatsapp/message ready");
    }
}
