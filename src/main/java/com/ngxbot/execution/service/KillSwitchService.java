package com.ngxbot.execution.service;

import com.ngxbot.common.exception.KillSwitchActiveException;
import com.ngxbot.notification.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hard kill switch that halts ALL trading immediately.
 * Separate from CircuitBreaker (which only halts SATELLITE pool on loss thresholds).
 * Kill switch is triggered by execution failures, reconciliation mismatches, or manual activation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KillSwitchService {

    private static final ZoneId WAT = ZoneId.of("Africa/Lagos");

    private final NotificationRouter notificationRouter;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<String> reason = new AtomicReference<>("");
    private final AtomicReference<ZonedDateTime> activatedAt = new AtomicReference<>();

    /**
     * Activates the kill switch. ALL trading stops immediately for BOTH markets.
     */
    public void activate(String triggerReason) {
        if (active.compareAndSet(false, true)) {
            reason.set(triggerReason);
            activatedAt.set(ZonedDateTime.now(WAT));
            log.error("KILL SWITCH ACTIVATED: {}", triggerReason);

            try {
                notificationRouter.sendUrgent(
                        "*KILL SWITCH ACTIVATED*\n" +
                        "Reason: " + triggerReason + "\n" +
                        "Time: " + ZonedDateTime.now(WAT) + "\n" +
                        "ALL TRADING HALTED. Manual intervention required.");
            } catch (Exception e) {
                log.error("Failed to send kill switch notification", e);
            }
        }
    }

    /**
     * Deactivates the kill switch. Only for manual reset.
     */
    public void deactivate() {
        if (active.compareAndSet(true, false)) {
            log.warn("Kill switch deactivated manually. Previous reason: {}", reason.get());
            reason.set("");
            activatedAt.set(null);

            try {
                notificationRouter.sendAlert("Kill switch deactivated. Trading may resume.");
            } catch (Exception e) {
                log.error("Failed to send kill switch deactivation notification", e);
            }
        }
    }

    /**
     * Checks if kill switch is active. Call this at EVERY decision point.
     * @throws KillSwitchActiveException if kill switch is active
     */
    public void checkOrThrow() {
        if (active.get()) {
            throw new KillSwitchActiveException("Kill switch active: " + reason.get());
        }
    }

    /**
     * Returns true if kill switch is active.
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Returns the reason for activation.
     */
    public String getReason() {
        return reason.get();
    }

    /**
     * Returns when the kill switch was activated.
     */
    public ZonedDateTime getActivatedAt() {
        return activatedAt.get();
    }
}
