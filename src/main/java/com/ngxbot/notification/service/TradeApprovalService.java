package com.ngxbot.notification.service;

import com.ngxbot.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages trade-approval workflows.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@link #requestApproval} sends an approval message via WhatsApp and blocks
 *       until the user replies or the timeout expires.</li>
 *   <li>The WhatsApp webhook calls {@link #processReply} when a reply arrives,
 *       completing the pending future.</li>
 *   <li>On timeout, the configured {@code defaultOnTimeout} value ("APPROVE" or
 *       "REJECT") is returned.</li>
 * </ol>
 */
@Slf4j
@Service
public class TradeApprovalService {

    private final NotificationRouter notificationRouter;
    private final MessageFormatter messageFormatter;
    private final NotificationProperties notificationProperties;

    /**
     * Map of pending approval futures keyed by approvalId.
     * CompletableFuture resolves to {@code true} for approved, {@code false} for rejected.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingApprovals =
            new ConcurrentHashMap<>();

    public TradeApprovalService(NotificationRouter notificationRouter,
                                MessageFormatter messageFormatter,
                                NotificationProperties notificationProperties) {
        this.notificationRouter = notificationRouter;
        this.messageFormatter = messageFormatter;
        this.notificationProperties = notificationProperties;
    }

    /**
     * Sends an approval request via WhatsApp and waits for a reply.
     *
     * @param approvalId unique identifier for this approval request
     * @param side       "BUY" or "SELL"
     * @param symbol     stock ticker
     * @param quantity   number of shares
     * @param price      limit price per share
     * @param currency   "NGN" or "USD"
     * @param riskPct    portfolio risk as decimal (0.02 = 2%)
     * @return true if approved, false if rejected or timed out with default=REJECT
     */
    public boolean requestApproval(String approvalId, String side, String symbol,
                                   int quantity, BigDecimal price, String currency,
                                   BigDecimal riskPct) {
        int timeoutMinutes = notificationProperties.getApproval().getTimeoutMinutes();
        String defaultOnTimeout = notificationProperties.getApproval().getDefaultOnTimeout();

        log.info("Requesting trade approval: id={}, {} {} x{} @ {} (timeout={}min, default={})",
                approvalId, side, symbol, quantity, price, timeoutMinutes, defaultOnTimeout);

        // Format and send the approval message via WhatsApp
        String message = messageFormatter.formatApprovalRequest(
                side, symbol, quantity, price, currency, riskPct, timeoutMinutes);
        notificationRouter.sendWhatsAppOnly(message);

        // Create a future for this approval
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingApprovals.put(approvalId, future);

        try {
            // Block until reply or timeout
            return future.get(timeoutMinutes, TimeUnit.MINUTES);

        } catch (TimeoutException e) {
            log.warn("Approval timed out for id={} after {} minutes — applying default: {}",
                    approvalId, timeoutMinutes, defaultOnTimeout);
            boolean approved = "APPROVE".equalsIgnoreCase(defaultOnTimeout);

            String timeoutNotice = String.format(
                    "*APPROVAL TIMED OUT*\nOrder: %s %s x%,d\nDefault action: %s",
                    side, symbol, quantity, defaultOnTimeout);
            notificationRouter.sendAlert(timeoutNotice);

            return approved;

        } catch (Exception e) {
            log.error("Approval request failed for id={}: {}", approvalId, e.getMessage(), e);
            return false;

        } finally {
            pendingApprovals.remove(approvalId);
        }
    }

    /**
     * Processes an incoming reply from the WhatsApp webhook.
     *
     * @param approvalId the approval identifier
     * @param reply      user reply text (e.g. "YES", "NO")
     */
    public void processReply(String approvalId, String reply) {
        CompletableFuture<Boolean> future = pendingApprovals.get(approvalId);

        if (future == null) {
            log.warn("No pending approval found for id={}", approvalId);
            return;
        }

        boolean approved = "YES".equalsIgnoreCase(reply.trim());
        log.info("Approval reply received: id={}, reply='{}', approved={}", approvalId, reply, approved);
        future.complete(approved);
    }

    /**
     * Checks if an approval request is currently pending.
     *
     * @param approvalId the approval identifier
     * @return true if a pending approval exists for the given id
     */
    public boolean hasPendingApproval(String approvalId) {
        return pendingApprovals.containsKey(approvalId);
    }

    /**
     * Checks if there are any pending approvals.
     *
     * @return true if at least one approval is pending
     */
    public boolean hasAnyPendingApproval() {
        return !pendingApprovals.isEmpty();
    }

    /**
     * Returns the set of all currently pending approval IDs.
     *
     * @return unmodifiable set of pending approval IDs
     */
    public Set<String> getPendingApprovalIds() {
        return Set.copyOf(pendingApprovals.keySet());
    }

    /**
     * Processes a reply for all currently pending approvals.
     * Typically there is at most one pending at any time. This method is
     * used by the webhook controller when it cannot determine a specific
     * approval ID from the incoming message.
     *
     * @param reply user reply text (e.g. "YES", "NO")
     */
    public void processAnyPendingReply(String reply) {
        if (pendingApprovals.isEmpty()) {
            log.warn("No pending approvals to process reply: '{}'", reply);
            return;
        }

        for (Map.Entry<String, CompletableFuture<Boolean>> entry : pendingApprovals.entrySet()) {
            processReply(entry.getKey(), reply);
        }
    }

    /**
     * DCA (Dollar Cost Averaging) trades are always auto-approved.
     *
     * @return true (always)
     */
    public boolean isDcaAutoApproved() {
        return true;
    }
}
