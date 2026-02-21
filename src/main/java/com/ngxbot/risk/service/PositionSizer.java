package com.ngxbot.risk.service;

import com.ngxbot.config.RiskProperties;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.StrategyMarket;
import com.ngxbot.strategy.StrategyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates optimal position size given risk parameters, settlement-aware cash,
 * and pool-specific limits.
 * <p>
 * Sizing formula:
 * <pre>
 *   riskAmount   = portfolioValue * maxRiskPerTradePct / 100
 *   stopDistance  = entryPrice - stopLoss
 *   quantity      = floor(riskAmount / stopDistance)
 * </pre>
 * The result is then capped by available cash and maximum position size rules.
 * <p>
 * CRITICAL: Uses {@link SettlementCashTracker#getAvailableCash(StrategyMarket)} --
 * never total cash. Only settled cash may be deployed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionSizer {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_STOP_PCT = new BigDecimal("0.08");
    private static final BigDecimal CORE_MAX_POSITION_PCT = new BigDecimal("20.0");

    /**
     * Result of position sizing calculation.
     *
     * @param quantity   number of shares to buy (rounded down)
     * @param tradeValue total cost of the position (quantity * entryPrice)
     * @param riskAmount dollar risk if stop-loss is hit
     * @param reasoning  human-readable explanation of how size was determined
     */
    public record PositionSizeResult(int quantity, BigDecimal tradeValue,
                                      BigDecimal riskAmount, String reasoning) {
    }

    private final RiskProperties riskProperties;
    private final SettlementCashTracker settlementCashTracker;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * Calculates the optimal position size for a given trade signal.
     *
     * @param signal the trade signal containing entry price and stop-loss
     * @param pool   CORE or SATELLITE — affects max position size
     * @param market NGX, US, or BOTH — determines which cash ledger to use
     * @return sizing result with quantity, trade value, risk, and reasoning
     */
    public PositionSizeResult calculatePositionSize(TradeSignal signal, StrategyPool pool, StrategyMarket market) {
        PortfolioSnapshot snapshot = getLatestSnapshot();
        BigDecimal portfolioValue = snapshot.getTotalValue();
        BigDecimal entryPrice = signal.suggestedPrice();
        BigDecimal stopLoss = resolveStopLoss(entryPrice, signal.stopLoss());

        // Step 1: Calculate risk-based quantity
        BigDecimal maxRiskPct = riskProperties.getMaxRiskPerTradePct();
        BigDecimal riskBudget = portfolioValue.multiply(maxRiskPct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal stopDistance = entryPrice.subtract(stopLoss);

        if (stopDistance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Stop distance is zero or negative for {} (entry={}, stop={}). Cannot size position.",
                    signal.symbol(), entryPrice, stopLoss);
            return new PositionSizeResult(0, BigDecimal.ZERO, BigDecimal.ZERO,
                    "Stop distance is zero or negative — no valid position size");
        }

        int riskBasedQuantity = riskBudget.divide(stopDistance, 0, RoundingMode.DOWN).intValue();

        // Step 2: Cap by max position size (pool-aware)
        BigDecimal maxPositionPct = pool == StrategyPool.CORE
                ? CORE_MAX_POSITION_PCT
                : riskProperties.getMaxSinglePositionPct();
        BigDecimal maxPositionValue = portfolioValue.multiply(maxPositionPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        int maxPositionQuantity = maxPositionValue.divide(entryPrice, 0, RoundingMode.DOWN).intValue();

        int quantity = Math.min(riskBasedQuantity, maxPositionQuantity);
        String capReason = "";
        if (riskBasedQuantity > maxPositionQuantity) {
            capReason = String.format(" Capped by max position size (%.1f%% of portfolio).", maxPositionPct);
        }

        // Step 3: Cap by available (settled) cash
        BigDecimal availableCash = settlementCashTracker.getAvailableCash(market);
        int cashBasedQuantity = availableCash.divide(entryPrice, 0, RoundingMode.DOWN).intValue();

        if (quantity > cashBasedQuantity) {
            quantity = cashBasedQuantity;
            capReason += String.format(" Reduced to fit available settled cash (%s).",
                    availableCash.toPlainString());
        }

        // Step 4: Ensure non-negative
        quantity = Math.max(quantity, 0);

        BigDecimal tradeValue = entryPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal actualRisk = stopDistance.multiply(BigDecimal.valueOf(quantity));
        BigDecimal riskPct = portfolioValue.compareTo(BigDecimal.ZERO) > 0
                ? actualRisk.multiply(HUNDRED).divide(portfolioValue, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String reasoning = String.format(
                "Risk budget: %s (%s%% of %s). Stop distance: %s. "
                        + "Risk-based qty: %d. Final qty: %d. Trade value: %s. "
                        + "Actual risk: %s (%.2f%% of portfolio). Pool: %s.%s",
                riskBudget.toPlainString(), maxRiskPct.toPlainString(), portfolioValue.toPlainString(),
                stopDistance.toPlainString(),
                riskBasedQuantity, quantity, tradeValue.toPlainString(),
                actualRisk.toPlainString(), riskPct, pool, capReason
        );

        log.info("Position sizing for {} [{}]: qty={}, value={}, risk={} ({}%)",
                signal.symbol(), pool, quantity, tradeValue.toPlainString(),
                actualRisk.toPlainString(), riskPct);

        return new PositionSizeResult(quantity, tradeValue, actualRisk, reasoning);
    }

    // ---- Private helpers ----

    private PortfolioSnapshot getLatestSnapshot() {
        return portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "No portfolio snapshot available. Cannot calculate position size."));
    }

    /**
     * If stopLoss is null or non-positive, defaults to 8% below entry price.
     */
    private BigDecimal resolveStopLoss(BigDecimal entryPrice, BigDecimal stopLoss) {
        if (stopLoss != null && stopLoss.compareTo(BigDecimal.ZERO) > 0) {
            return stopLoss;
        }
        BigDecimal defaultStop = entryPrice.multiply(BigDecimal.ONE.subtract(DEFAULT_STOP_PCT));
        log.debug("No stop-loss provided. Using 8% default stop: {}", defaultStop);
        return defaultStop;
    }
}
