package com.ngxbot.execution.service;

import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.notification.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Handles recovery from execution failures.
 * On ANY Playwright failure mid-order:
 * 1. Mark order as UNCERTAIN
 * 2. Activate kill switch
 * 3. Send urgent WhatsApp alert with screenshot
 * 4. Attempt to verify order status from broker history
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderRecoveryService {

    private final TradeOrderRepository tradeOrderRepository;
    private final KillSwitchService killSwitchService;
    private final NotificationRouter notificationRouter;

    /**
     * Handles an execution failure. Marks order UNCERTAIN and activates kill switch.
     */
    public TradeOrder handleExecutionFailure(TradeOrder order, Exception error) {
        log.error("[RECOVERY] Execution failure for order {}: {}", order.getOrderId(), error.getMessage());

        // 1. Mark order as UNCERTAIN
        order.setStatus("UNCERTAIN");
        order.setErrorMessage("Execution failure: " + error.getMessage());
        order.setUpdatedAt(LocalDateTime.now());
        order = tradeOrderRepository.save(order);

        // 2. Activate kill switch
        killSwitchService.activate("Execution failure on order " + order.getOrderId() +
                ": " + error.getMessage());

        // 3. Send urgent alert
        try {
            String alertMessage = String.format(
                    "*EXECUTION FAILURE — URGENT*\n" +
                    "Order: %s\n" +
                    "Symbol: %s %s\n" +
                    "Qty: %d @ %s\n" +
                    "Error: %s\n\n" +
                    "Order status is UNCERTAIN. Kill switch activated.\n" +
                    "Check broker manually to verify if order was executed.",
                    order.getOrderId(), order.getSide(), order.getSymbol(),
                    order.getQuantity(), order.getIntendedPrice(),
                    error.getMessage());

            notificationRouter.sendUrgent(alertMessage);
        } catch (Exception notifError) {
            log.error("[RECOVERY] Failed to send alert notification", notifError);
        }

        // 4. Attempt to verify via broker (best effort)
        attemptOrderVerification(order);

        return order;
    }

    /**
     * Attempts to verify an uncertain order by checking broker order history.
     * This is best-effort — if it fails, the order remains UNCERTAIN.
     */
    private void attemptOrderVerification(TradeOrder order) {
        log.info("[RECOVERY] Attempting to verify order {} status from broker", order.getOrderId());

        // PLACEHOLDER: Would use BrokerGateway.checkOrderStatus()
        // If verification succeeds:
        //   - Update order status to CONFIRMED or FAILED based on broker state
        //   - Notify user of resolution
        // If verification fails:
        //   - Order remains UNCERTAIN
        //   - User must manually verify

        log.warn("[RECOVERY] Order {} remains UNCERTAIN — manual verification required", order.getOrderId());
    }

    /**
     * Resolves an uncertain order manually.
     */
    public TradeOrder resolveUncertainOrder(String orderId, String resolution, String notes) {
        TradeOrder order = tradeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"UNCERTAIN".equals(order.getStatus())) {
            throw new IllegalStateException("Order " + orderId + " is not UNCERTAIN (status: " + order.getStatus() + ")");
        }

        order.setStatus(resolution); // CONFIRMED or FAILED
        order.setErrorMessage(order.getErrorMessage() + " | Resolution: " + notes);
        order.setUpdatedAt(LocalDateTime.now());
        order = tradeOrderRepository.save(order);

        log.info("[RECOVERY] Order {} resolved as {}: {}", orderId, resolution, notes);
        notificationRouter.sendAlert("Order " + orderId + " resolved as " + resolution + ": " + notes);

        return order;
    }
}
