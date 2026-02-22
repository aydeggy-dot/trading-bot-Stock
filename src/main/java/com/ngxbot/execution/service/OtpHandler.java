package com.ngxbot.execution.service;

import com.ngxbot.notification.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles OTP verification during broker login.
 * Sends WhatsApp prompt to user, waits for OTP reply, enters code.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpHandler {

    private final NotificationRouter notificationRouter;

    private static final int OTP_TIMEOUT_SECONDS = 120;
    private static final int MAX_OTP_RETRIES = 3;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingOtps = new ConcurrentHashMap<>();

    /**
     * Requests OTP from user via WhatsApp and waits for reply.
     * @return the OTP code entered by user
     * @throws TimeoutException if user doesn't respond within timeout
     */
    public String requestOtp() throws Exception {
        String requestId = "otp-" + System.currentTimeMillis();

        CompletableFuture<String> otpFuture = new CompletableFuture<>();
        pendingOtps.put(requestId, otpFuture);

        try {
            notificationRouter.sendWhatsAppOnly(
                    "*OTP REQUIRED*\n" +
                    "Broker is requesting an OTP code.\n" +
                    "Please reply with the OTP code (digits only).");

            log.info("OTP requested via WhatsApp, waiting up to {}s for reply", OTP_TIMEOUT_SECONDS);
            String otp = otpFuture.get(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("OTP received: {}", otp.replaceAll(".", "*"));
            return otp;

        } catch (TimeoutException e) {
            log.error("OTP timeout after {}s", OTP_TIMEOUT_SECONDS);
            throw new TimeoutException("OTP not received within " + OTP_TIMEOUT_SECONDS + " seconds");
        } finally {
            pendingOtps.remove(requestId);
        }
    }

    /**
     * Called by webhook when user replies with OTP code.
     */
    public void processOtpReply(String otpCode) {
        String cleanOtp = otpCode.replaceAll("[^0-9]", "");
        if (cleanOtp.isEmpty()) {
            log.warn("Received non-numeric OTP reply: {}", otpCode);
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
