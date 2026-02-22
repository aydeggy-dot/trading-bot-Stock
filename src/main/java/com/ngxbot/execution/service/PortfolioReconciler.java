package com.ngxbot.execution.service;

import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciles internal DB positions with broker's actual portfolio.
 * Runs on startup and daily before trading.
 * On mismatch: ALERT + HALT trading via kill switch.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioReconciler {

    private final PositionRepository positionRepository;
    private final KillSwitchService killSwitchService;
    private final NotificationRouter notificationRouter;

    // BrokerGateway is optional
    private final BrokerGateway brokerGateway;

    /**
     * Reconciles internal positions with broker holdings.
     * @return true if all positions match, false if mismatches found
     */
    public boolean reconcile() {
        log.info("[RECONCILE] Starting portfolio reconciliation");

        try {
            // Get broker holdings
            Map<String, Integer> brokerHoldings = brokerGateway.getPortfolioHoldings();

            // Get internal open positions
            Map<String, Integer> internalHoldings = getInternalHoldings();

            // Compare
            Map<String, String> mismatches = new HashMap<>();
            for (Map.Entry<String, Integer> entry : internalHoldings.entrySet()) {
                String symbol = entry.getKey();
                int internalQty = entry.getValue();
                int brokerQty = brokerHoldings.getOrDefault(symbol, 0);

                if (internalQty != brokerQty) {
                    mismatches.put(symbol,
                            String.format("Internal: %d, Broker: %d", internalQty, brokerQty));
                }
            }

            // Check for positions on broker not in our DB
            for (Map.Entry<String, Integer> entry : brokerHoldings.entrySet()) {
                if (!internalHoldings.containsKey(entry.getKey())) {
                    mismatches.put(entry.getKey(),
                            String.format("Internal: 0, Broker: %d (unknown position)", entry.getValue()));
                }
            }

            if (mismatches.isEmpty()) {
                log.info("[RECONCILE] Portfolio reconciliation passed — all positions match");
                return true;
            }

            // Mismatches found
            StringBuilder alert = new StringBuilder("*PORTFOLIO MISMATCH DETECTED*\n");
            mismatches.forEach((symbol, detail) ->
                    alert.append(String.format("- %s: %s\n", symbol, detail)));
            alert.append("\nTrading halted. Manual reconciliation required.");

            log.error("[RECONCILE] {}", alert);
            killSwitchService.activate("Portfolio reconciliation mismatch: " + mismatches.size() + " discrepancies");
            notificationRouter.sendUrgent(alert.toString());
            return false;

        } catch (Exception e) {
            log.error("[RECONCILE] Failed to reconcile portfolio", e);
            notificationRouter.sendUrgent("*RECONCILIATION FAILED*\nError: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Integer> getInternalHoldings() {
        Map<String, Integer> holdings = new HashMap<>();
        // Fetch all open positions and aggregate by symbol
        // Note: PositionRepository should have a method to get all open positions
        // Using a simple query approach
        List<Position> openPositions = positionRepository.findAll().stream()
                .filter(p -> p.getIsOpen() != null && p.getIsOpen())
                .toList();

        for (Position pos : openPositions) {
            holdings.merge(pos.getSymbol(), pos.getQuantity(), Integer::sum);
        }
        return holdings;
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "Africa/Lagos")
    public void dailyReconciliation() {
        log.info("[RECONCILE] Running scheduled daily reconciliation");
        reconcile();
    }
}
