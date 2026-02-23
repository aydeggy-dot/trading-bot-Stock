package com.ngxbot.execution.service;

import com.ngxbot.notification.service.NotificationRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles OTP verification during broker login.
 *
 * Strategy (in order):
 *   1. Email IMAP — automatically reads OTP from Gmail (if otp.email.enabled=true)
 *   2. WhatsApp prompt — sends message to user, waits for manual reply
 *
 * The email path is fully autonomous. The WhatsApp path requires human interaction.
 */
@Service
@Slf4j
public class OtpHandler {

    private final NotificationRouter notificationRouter;
    private final EmailOtpReader emailOtpReader; // null if otp.email.enabled != true

    private static final int WHATSAPP_OTP_TIMEOUT_SECONDS = 120;
    private static final int MAX_OTP_RETRIES = 3;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingOtps = new ConcurrentHashMap<>();

    public OtpHandler(NotificationRouter notificationRouter,
                      // Optional — only injected if otp.email.enabled=true
                      @org.springframework.beans.factory.annotation.Autowired(required = false)
                      EmailOtpReader emailOtpReader) {
        this.notificationRouter = notificationRouter;
        this.emailOtpReader = emailOtpReader;

        if (emailOtpReader != null) {
            log.info("[OTP] Email OTP reader is ENABLED — will read OTP from Gmail automatically");
        } else {
            log.info("[OTP] Email OTP reader is DISABLED — will prompt via WhatsApp for manual OTP entry");
        }
    }

    /**
     * Requests OTP — tries email IMAP first, falls back to WhatsApp prompt.
     * @return the OTP code
     * @throws Exception if OTP cannot be obtained
     */
    public String requestOtp() throws Exception {
        // Strategy 1: Read OTP from email (fully autonomous)
        if (emailOtpReader != null) {
            try {
                log.info("[OTP] Attempting to read OTP from email (autonomous)");
                String otp = emailOtpReader.readOtpFromEmail();
                if (otp != null && !otp.isEmpty()) {
                    log.info("[OTP] Got OTP from email successfully");
                    return otp;
                }
            } catch (Exception e) {
                log.warn("[OTP] Email OTP reading failed: {}. Falling back to WhatsApp.", e.getMessage());
            }
        }

        // Strategy 2: WhatsApp prompt (manual)
        return requestOtpViaWhatsApp();
    }

    /**
     * Sends WhatsApp prompt to user and waits for manual OTP reply.
     */
    private String requestOtpViaWhatsApp() throws Exception {
        String requestId = "otp-" + System.currentTimeMillis();

        CompletableFuture<String> otpFuture = new CompletableFuture<>();
        pendingOtps.put(requestId, otpFuture);

        try {
            notificationRouter.sendWhatsAppOnly(
                    "*OTP REQUIRED*\n" +
                    "Broker is requesting an OTP code.\n" +
                    "Please reply with the OTP code (digits only).");

            log.info("[OTP] OTP requested via WhatsApp, waiting up to {}s for reply",
                    WHATSAPP_OTP_TIMEOUT_SECONDS);
            String otp = otpFuture.get(WHATSAPP_OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("[OTP] OTP received via WhatsApp: {}", otp.replaceAll(".", "*"));
            return otp;

        } catch (TimeoutException e) {
            log.error("[OTP] WhatsApp OTP timeout after {}s", WHATSAPP_OTP_TIMEOUT_SECONDS);
            throw new TimeoutException("OTP not received within " + WHATSAPP_OTP_TIMEOUT_SECONDS + " seconds");
        } finally {
            pendingOtps.remove(requestId);
        }
    }

    /**
     * Called by webhook when user replies with OTP code via WhatsApp.
     */
    public void processOtpReply(String otpCode) {
        String cleanOtp = otpCode.replaceAll("[^0-9]", "");
        if (cleanOtp.isEmpty()) {
            log.warn("[OTP] Received non-numeric OTP reply: {}", otpCode);
            return;
        }

        pendingOtps.values().forEach(future -> {
            if (!future.isDone()) {
                future.complete(cleanOtp);
            }
        });
    }

    /**
     * Returns true if there's a pending OTP request.
     */
    public boolean hasPendingOtp() {
        return !pendingOtps.isEmpty();
    }

    /**
     * Returns the max number of OTP retries.
     */
    public int getMaxRetries() {
        return MAX_OTP_RETRIES;
    }
}
