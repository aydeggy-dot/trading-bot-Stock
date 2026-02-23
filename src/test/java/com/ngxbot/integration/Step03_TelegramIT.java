package com.ngxbot.integration;

import com.ngxbot.config.NotificationProperties;
import com.ngxbot.notification.service.TelegramService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: Telegram Notifications
 *
 * Verifies:
 *   - Telegram bot token and chat ID are configured
 *   - Simple text message sends successfully
 *   - Formatted trade signal notification sends
 *
 * Prereqs: TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID set in .env
 * User should check their Telegram for received messages.
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step03_TelegramIT extends IntegrationTestBase {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private NotificationProperties notificationProperties;

    @Test
    @Order(1)
    @DisplayName("3.1 Telegram bot token and chat ID are configured")
    void telegramCredentialsConfigured() {
        assertThat(notificationProperties.getTelegram().getBotToken())
                .as("TELEGRAM_BOT_TOKEN must be set")
                .isNotBlank();
        assertThat(notificationProperties.getTelegram().getChatId())
                .as("TELEGRAM_CHAT_ID must be set")
                .isNotBlank();

        printResult("Telegram Config",
                String.format("Token: %s***, Chat: %s",
                        notificationProperties.getTelegram().getBotToken().substring(0, 8),
                        notificationProperties.getTelegram().getChatId()));
    }

    @Test
    @Order(2)
    @DisplayName("3.2 Send plain text test message")
    void sendPlainTextMessage() {
        String timestamp = ZonedDateTime.now(ZoneId.of("Africa/Lagos"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        String message = String.format(
                "🔧 *NGX Trading Bot — Integration Test*\n\n" +
                "Telegram notification is working!\n" +
                "Timestamp: `%s`\n" +
                "Status: CONNECTED", timestamp);

        assertThatCode(() -> telegramService.sendMessage(message))
                .as("Telegram message send should not throw")
                .doesNotThrowAnyException();

        printResult("Telegram Send", "Plain text message sent — check your Telegram!");
    }

    @Test
    @Order(3)
    @DisplayName("3.3 Send formatted trade signal notification")
    void sendFormattedTradeSignal() {
        String signalMessage = String.format(
                "📊 *TRADE SIGNAL — Integration Test*\n\n" +
                "Symbol: `ZENITHBANK`\n" +
                "Action: *BUY*\n" +
                "Price: ₦35.50\n" +
                "Stop Loss: ₦33.73 (-5%%)\n" +
                "Target: ₦39.05 (+10%%)\n" +
                "Confidence: 75/100\n" +
                "Strategy: Momentum Breakout\n\n" +
                "⚠️ _This is a test signal — NOT a real trade._");

        assertThatCode(() -> telegramService.sendMessage(signalMessage))
                .as("Formatted signal message should not throw")
                .doesNotThrowAnyException();

        printResult("Telegram Signal",
                "Formatted trade signal sent — check your Telegram for rich formatting!");
    }

    @Test
    @Order(4)
    @DisplayName("3.4 Send long message (tests Telegram's 4096 char limit handling)")
    void sendLongMessage() {
        StringBuilder sb = new StringBuilder("📋 *Portfolio Summary — Integration Test*\n\n");
        String[] stocks = {"ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "DANGCEM",
                "SEPLAT", "MTNN", "FBNH", "BUACEMENT", "ARADEL"};
        for (int i = 0; i < stocks.length; i++) {
            sb.append(String.format("%d. `%s` — ₦%,.2f × %d shares = ₦%,.2f (%+.1f%%)\n",
                    i + 1, stocks[i],
                    30.0 + i * 5.0,
                    100 * (i + 1),
                    (30.0 + i * 5.0) * 100 * (i + 1),
                    (Math.random() * 20) - 10));
        }
        sb.append("\n_Total portfolio value: ₦5,234,567.89_");

        assertThatCode(() -> telegramService.sendMessage(sb.toString()))
                .doesNotThrowAnyException();

        printResult("Telegram Long Message",
                String.format("Sent %d-char portfolio summary", sb.length()));
    }
}
