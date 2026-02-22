package com.ngxbot.execution.service;

import com.ngxbot.notification.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Captures and reconciles Trove's displayed FX rate vs market rate.
 * The spread is a real cost that erodes returns on US trades.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FxRateReconciler {

    private final BrokerGateway brokerGateway;
    private final NotificationRouter notificationRouter;

    private volatile BigDecimal lastBrokerRate = BigDecimal.ZERO;
    private volatile BigDecimal lastMarketRate = BigDecimal.ZERO;

    private static final BigDecimal MAX_SPREAD_PCT = new BigDecimal("3.0"); // Alert if spread > 3%

    /**
     * Captures the broker's FX rate and compares to market rate.
     */
    public BigDecimal reconcile(BigDecimal marketRate) {
        try {
            BigDecimal brokerRate = brokerGateway.getBrokerFxRate();
            if (brokerRate.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[FX-RECONCILE] Broker FX rate unavailable");
                return BigDecimal.ZERO;
            }

            lastBrokerRate = brokerRate;
            lastMarketRate = marketRate;

            BigDecimal spreadPct = BigDecimal.ZERO;
            if (marketRate.compareTo(BigDecimal.ZERO) > 0) {
                spreadPct = brokerRate.subtract(marketRate).abs()
                        .divide(marketRate, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            log.info("[FX-RECONCILE] Broker rate: {}, Market rate: {}, Spread: {}%",
                    brokerRate, marketRate, spreadPct);

            if (spreadPct.compareTo(MAX_SPREAD_PCT) > 0) {
                notificationRouter.sendAlert(String.format(
                        "*FX SPREAD ALERT*\nBroker: %s NGN/USD\nMarket: %s NGN/USD\nSpread: %s%%\n" +
                        "Consider delaying US trades until spread narrows.",
                        brokerRate, marketRate, spreadPct));
            }

            return spreadPct;

        } catch (Exception e) {
            log.error("[FX-RECONCILE] Failed to reconcile FX rate", e);
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getLastBrokerRate() {
        return lastBrokerRate;
    }

    public BigDecimal getLastMarketRate() {
        return lastMarketRate;
    }
}
