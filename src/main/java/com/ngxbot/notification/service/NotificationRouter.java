package com.ngxbot.notification.service;

import com.ngxbot.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Routes notification messages to the appropriate channel(s).
 * <p>
 * Routing strategies:
 * <ul>
 *   <li>{@link #sendAlert} — WhatsApp first, Telegram fallback</li>
 *   <li>{@link #sendUrgent} — both WhatsApp AND Telegram</li>
 *   <li>{@link #sendWhatsAppOnly} — WhatsApp only (OTP, approval flows)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouter {

    private final WhatsAppService whatsAppService;
    private final TelegramService telegramService;
    private final NotificationProperties notificationProperties;

    /**
     * Sends an alert via WhatsApp first; falls back to Telegram if WhatsApp
     * fails or is disabled.
     *
     * @param message the alert text
     */
    public void sendAlert(String message) {
        boolean whatsAppSent = false;

        if (notificationProperties.getWhatsapp().isEnabled()) {
            try {
                whatsAppService.sendMessage(message);
                whatsAppSent = true;
            } catch (Exception e) {
                log.warn("WhatsApp alert failed, falling back to Telegram: {}", e.getMessage());
            }
        }

        if (!whatsAppSent) {
            log.info("Routing alert to Telegram (WhatsApp unavailable or disabled)");
            try {
                telegramService.sendMessage(message);
            } catch (Exception e) {
                log.error("Both WhatsApp and Telegram alert delivery failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Sends an urgent notification to BOTH WhatsApp and Telegram simultaneously.
     *
     * @param message the urgent message
     */
    public void sendUrgent(String message) {
        log.info("Sending urgent notification to all channels");

        try {
            whatsAppService.sendMessage(message);
        } catch (Exception e) {
            log.error("WhatsApp urgent send failed: {}", e.getMessage(), e);
        }

        try {
            telegramService.sendMessage(message);
        } catch (Exception e) {
            log.error("Telegram urgent send failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends a message exclusively via WhatsApp.
     * Used for OTP delivery, trade approval requests, and other interactive flows
     * that require the WhatsApp webhook for replies.
     *
     * @param message the message text
     */
    public void sendWhatsAppOnly(String message) {
        try {
            whatsAppService.sendMessage(message);
        } catch (Exception e) {
            log.error("WhatsApp-only send failed: {}", e.getMessage(), e);
        }
    }
}
