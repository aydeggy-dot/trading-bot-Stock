package com.ngxbot.risk.service;

import com.ngxbot.config.RiskProperties;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.entity.RiskCheckResult;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.StrategyMarket;
import com.ngxbot.strategy.StrategyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Central risk check orchestrator. Runs all pre-trade risk validations
 * against configurable thresholds and returns a composite result.
 * <p>
 * Pool-aware: CORE positions enjoy relaxed single-position limits (20%),
 * while SATELLITE positions use standard limits from {@link RiskProperties}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManager {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal CORE_POSITION_SIZE_PCT = new BigDecimal("20.0");

    private final RiskProperties riskProperties;
    private final PositionRepository positionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * Runs ALL risk checks for a proposed trade and returns every result,
     * regardless of pass/fail. Callers should inspect each result.
     *
     * @param signal the trade signal to validate
     * @param pool   CORE or SATELLITE pool
     * @param market NGX, US, or BOTH
     * @return list of all risk check results
     */
    public List<RiskCheckResult> validateTrade(TradeSignal signal, StrategyPool pool, StrategyMarket market) {
        List<RiskCheckResult> results = new ArrayList<>();

        PortfolioSnapshot snapshot = getLatestSnapshot();
        BigDecimal portfolioValue = snapshot.getTotalValue();
        BigDecimal cashAvailable = snapshot.getCashBalance();

        BigDecimal entryPrice = signal.suggestedPrice();
        BigDecimal stopLoss = signal.stopLoss();

        // Estimate trade value — use a rough position-size estimate for exposure checks
        BigDecimal riskPerTrade = resolveMaxRiskPerTradePct();
        BigDecimal riskAmount = portfolioValue.multiply(riskPerTrade).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal stopDistance = calculateStopDistance(entryPrice, stopLoss);
        int estimatedQuantity = stopDistance.compareTo(BigDecimal.ZERO) > 0
                ? riskAmount.divide(stopDistance, 0, RoundingMode.DOWN).intValue()
                : 0;
        BigDecimal tradeValue = entryPrice.multiply(BigDecimal.valueOf(Math.max(estimatedQuantity, 1)));

        // 1. Max open positions
        results.add(checkMaxPositions());

        // 2. Single position size (pool-aware)
        BigDecimal maxPositionPct = pool == StrategyPool.CORE
                ? CORE_POSITION_SIZE_PCT
                : riskProperties.getMaxSinglePositionPct();
        results.add(checkSinglePositionSize(tradeValue, portfolioValue, maxPositionPct));

        // 3. Sector exposure
        String sector = deriveSector(signal.symbol());
        BigDecimal currentSectorExposure = calculateSectorExposure(sector);
        BigDecimal newSectorExposure = currentSectorExposure.add(tradeValue);
        results.add(checkSectorExposure(sector, newSectorExposure, portfolioValue));

        // 4. Cash reserve
        results.add(checkCashReserve(cashAvailable, portfolioValue));

        // 5. Risk per trade
        results.add(checkRiskPerTrade(entryPrice, stopLoss, estimatedQuantity, portfolioValue));

        log.info("Risk validation for {} ({}/{}): {} checks run, {} passed",
                signal.symbol(), pool, market,
                results.size(),
                results.stream().filter(RiskCheckResult::passed).count());

        return results;
    }

    /**
     * Convenience method: returns true only if ALL risk checks pass.
     */
    public boolean isTradeAllowed(TradeSignal signal, StrategyPool pool, StrategyMarket market) {
        List<RiskCheckResult> results = validateTrade(signal, pool, market);
        boolean allPassed = results.stream().allMatch(RiskCheckResult::passed);
        if (!allPassed) {
            results.stream()
                    .filter(r -> !r.passed())
                    .forEach(r -> log.warn("Risk check FAILED [{}]: {} | violations: {}",
                            r.checkName(), r.description(), r.violations()));
        }
        return allPassed;
    }

    /**
     * Checks that the number of open positions does not exceed the configured maximum.
     */
    public RiskCheckResult checkMaxPositions() {
        long openCount = positionRepository.countOpenPositions();
        int maxAllowed = riskProperties.getMaxOpenPositions();

        if (openCount >= maxAllowed) {
            return RiskCheckResult.fail(
                    "MAX_POSITIONS",
                    String.format("Open positions (%d) at or above maximum (%d)", openCount, maxAllowed),
                    List.of(String.format("openPositions=%d, max=%d", openCount, maxAllowed))
            );
        }
        return RiskCheckResult.pass("MAX_POSITIONS");
    }

    /**
     * Checks that a single position does not exceed the maximum allowed percentage of portfolio value.
     *
     * @param tradeValue     total value of the proposed trade
     * @param portfolioValue total portfolio value
     * @return risk check result
     */
    public RiskCheckResult checkSinglePositionSize(BigDecimal tradeValue, BigDecimal portfolioValue) {
        return checkSinglePositionSize(tradeValue, portfolioValue, riskProperties.getMaxSinglePositionPct());
    }

    /**
     * Overloaded: checks against a specific maximum position percentage (pool-aware).
     */
    public RiskCheckResult checkSinglePositionSize(BigDecimal tradeValue, BigDecimal portfolioValue,
                                                    BigDecimal maxPositionPct) {
        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("SINGLE_POSITION_SIZE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        BigDecimal positionPct = tradeValue.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);

        if (positionPct.compareTo(maxPositionPct) > 0) {
            return RiskCheckResult.fail(
                    "SINGLE_POSITION_SIZE",
                    String.format("Position size %.2f%% exceeds maximum %.2f%%",
                            positionPct, maxPositionPct),
                    List.of(String.format("positionPct=%.4f%%, max=%.2f%%", positionPct, maxPositionPct))
            );
        }
        return RiskCheckResult.pass("SINGLE_POSITION_SIZE");
    }

    /**
     * Checks that total sector exposure (including the new trade) does not exceed the configured maximum.
     *
     * @param sector         sector name
     * @param newExposure    total sector exposure after proposed trade
     * @param portfolioValue total portfolio value
     * @return risk check result
     */
    public RiskCheckResult checkSectorExposure(String sector, BigDecimal newExposure, BigDecimal portfolioValue) {
        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("SECTOR_EXPOSURE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        BigDecimal exposurePct = newExposure.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);
        BigDecimal maxSectorPct = riskProperties.getMaxSectorExposurePct();

        if (exposurePct.compareTo(maxSectorPct) > 0) {
            return RiskCheckResult.fail(
                    "SECTOR_EXPOSURE",
                    String.format("Sector '%s' exposure %.2f%% exceeds maximum %.2f%%",
                            sector, exposurePct, maxSectorPct),
                    List.of(String.format("sector=%s, exposurePct=%.4f%%, max=%.2f%%",
                            sector, exposurePct, maxSectorPct))
            );
        }
        return RiskCheckResult.pass("SECTOR_EXPOSURE");
    }

    /**
     * Checks that the remaining cash after this trade still meets the minimum cash reserve requirement.
     *
     * @param cashAvailable  current available cash
     * @param portfolioValue total portfolio value
     * @return risk check result
     */
    public RiskCheckResult checkCashReserve(BigDecimal cashAvailable, BigDecimal portfolioValue) {
        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("CASH_RESERVE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        BigDecimal cashPct = cashAvailable.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);
        BigDecimal minCashPct = riskProperties.getMinCashReservePct();

        if (cashPct.compareTo(minCashPct) < 0) {
            return RiskCheckResult.fail(
                    "CASH_RESERVE",
                    String.format("Cash reserve %.2f%% below minimum %.2f%%", cashPct, minCashPct),
                    List.of(String.format("cashPct=%.4f%%, min=%.2f%%", cashPct, minCashPct))
            );
        }
        return RiskCheckResult.pass("CASH_RESERVE");
    }

    /**
     * Checks that the risk (loss if stop-loss is hit) does not exceed the maximum risk per trade.
     *
     * @param entryPrice     proposed entry price
     * @param stopLoss       stop-loss price (null triggers 8% default)
     * @param quantity        number of shares
     * @param portfolioValue total portfolio value
     * @return risk check result
     */
    public RiskCheckResult checkRiskPerTrade(BigDecimal entryPrice, BigDecimal stopLoss,
                                              int quantity, BigDecimal portfolioValue) {
        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("RISK_PER_TRADE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        BigDecimal effectiveStop = resolveStopLoss(entryPrice, stopLoss);
        BigDecimal stopDistance = entryPrice.subtract(effectiveStop);
        BigDecimal riskAmount = stopDistance.multiply(BigDecimal.valueOf(quantity));
        BigDecimal riskPct = riskAmount.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);
        BigDecimal maxRiskPct = riskProperties.getMaxRiskPerTradePct();

        if (riskPct.compareTo(maxRiskPct) > 0) {
            return RiskCheckResult.fail(
                    "RISK_PER_TRADE",
                    String.format("Trade risk %.2f%% exceeds maximum %.2f%% of portfolio", riskPct, maxRiskPct),
                    List.of(String.format("riskPct=%.4f%%, max=%.2f%%, riskAmount=%s",
                            riskPct, maxRiskPct, riskAmount.toPlainString()))
            );
        }
        return RiskCheckResult.pass("RISK_PER_TRADE");
    }

    // ---- Private helpers ----

    private PortfolioSnapshot getLatestSnapshot() {
        return portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "No portfolio snapshot available. Cannot perform risk checks."));
    }

    private BigDecimal resolveStopLoss(BigDecimal entryPrice, BigDecimal stopLoss) {
        if (stopLoss != null && stopLoss.compareTo(BigDecimal.ZERO) > 0) {
            return stopLoss;
        }
        // Default: 8% below entry
        return entryPrice.multiply(new BigDecimal("0.92"));
    }

    private BigDecimal calculateStopDistance(BigDecimal entryPrice, BigDecimal stopLoss) {
        BigDecimal effectiveStop = resolveStopLoss(entryPrice, stopLoss);
        BigDecimal distance = entryPrice.subtract(effectiveStop);
        return distance.compareTo(BigDecimal.ZERO) > 0 ? distance : BigDecimal.ONE;
    }

    private BigDecimal resolveMaxRiskPerTradePct() {
        return riskProperties.getMaxRiskPerTradePct();
    }

    private BigDecimal calculateSectorExposure(String sector) {
        if (sector == null || sector.isBlank()) {
            return BigDecimal.ZERO;
        }
        List<Position> sectorPositions = positionRepository.findBySectorAndIsOpenTrue(sector);
        return sectorPositions.stream()
                .map(p -> p.getCurrentPrice() != null
                        ? p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity()))
                        : p.getAvgEntryPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Derives sector from symbol. In a full implementation this would look up
     * from a reference table; for now returns the sector stored on existing
     * positions for this symbol, or "UNKNOWN".
     */
    private String deriveSector(String symbol) {
        List<Position> existing = positionRepository.findBySymbolAndIsOpenTrue(symbol);
        if (!existing.isEmpty() && existing.get(0).getSector() != null) {
            return existing.get(0).getSector();
        }
        return "UNKNOWN";
    }
}
