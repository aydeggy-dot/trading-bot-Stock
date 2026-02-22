package com.ngxbot.execution.service;

import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.strategy.StrategyMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Reconciles internal cash ledger with broker's actual cash balance.
 * On mismatch: ALERT + HALT trading.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CashReconciler {

    private final SettlementCashTracker settlementCashTracker;
    private final KillSwitchService killSwitchService;
    private final NotificationRouter notificationRouter;
    private final BrokerGateway brokerGateway;

    private static final BigDecimal TOLERANCE_PCT = new BigDecimal("0.02"); // 2% tolerance

    /**
     * Reconciles internal cash with broker for a specific market.
     */
    public boolean reconcile(StrategyMarket market) {
        String marketStr = market.name();
        log.info("[CASH-RECONCILE] Reconciling cash for market {}", marketStr);

        try {
            BigDecimal internalCash = settlementCashTracker.getTotalCash(market);
            BigDecimal brokerCash = brokerGateway.getAvailableCash(marketStr);

            if (brokerCash.compareTo(BigDecimal.ZERO) == 0 && internalCash.compareTo(BigDecimal.ZERO) == 0) {
                log.info("[CASH-RECONCILE] Both internal and broker cash are zero for {}", marketStr);
                return true;
            }

            BigDecimal difference = internalCash.subtract(brokerCash).abs();
            BigDecimal maxValue = internalCash.max(brokerCash);
            BigDecimal diffPct = maxValue.compareTo(BigDecimal.ZERO) > 0
                    ? difference.divide(maxValue, 4, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (diffPct.compareTo(TOLERANCE_PCT) <= 0) {
                log.info("[CASH-RECONCILE] {} cash reconciled (diff={}%)", marketStr, diffPct.multiply(new BigDecimal("100")));
                return true;
            }

            // Mismatch exceeds tolerance
            String alert = String.format(
                    "*CASH MISMATCH — %s*\nInternal: %s\nBroker: %s\nDifference: %s (%.2f%%)\n\nTrading halted.",
                    marketStr, internalCash, brokerCash, difference,
                    diffPct.multiply(new BigDecimal("100")).doubleValue());

            log.error("[CASH-RECONCILE] {}", alert);
            killSwitchService.activate("Cash reconciliation mismatch for " + marketStr);
            notificationRouter.sendUrgent(alert);
            return false;

        } catch (Exception e) {
            log.error("[CASH-RECONCILE] Failed to reconcile cash for {}", marketStr, e);
            return false;
        }
    }

    @Scheduled(cron = "0 35 9 * * MON-FRI", zone = "Africa/Lagos")
    public void dailyCashReconciliation() {
        reconcile(StrategyMarket.NGX);
        reconcile(StrategyMarket.US);
    }
}
