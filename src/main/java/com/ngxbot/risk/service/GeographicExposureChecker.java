package com.ngxbot.risk.service;

import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.entity.RiskCheckResult;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.strategy.StrategyMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Enforces maximum 75% exposure in any single geographic market (NGX vs US).
 * <p>
 * Prevents over-concentration in one market when the portfolio spans
 * both Nigerian and US equities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeographicExposureChecker {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_GEOGRAPHIC_EXPOSURE_PCT = new BigDecimal("75.0");

    private final PositionRepository positionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * Checks whether adding a new trade in the given market would push
     * that market's allocation above 75% of total portfolio value.
     *
     * @param market        the market of the proposed trade
     * @param newTradeValue the value of the proposed trade
     * @return risk check result indicating pass or fail
     */
    public RiskCheckResult checkGeographicExposure(StrategyMarket market, BigDecimal newTradeValue) {
        if (market == StrategyMarket.BOTH) {
            // BOTH-market trades do not concentrate in a single geography
            return RiskCheckResult.pass("GEOGRAPHIC_EXPOSURE");
        }

        PortfolioSnapshot snapshot = getLatestSnapshot();
        BigDecimal portfolioValue = snapshot.getTotalValue();

        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("GEOGRAPHIC_EXPOSURE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        BigDecimal currentMarketExposure = calculateMarketExposure(market);
        BigDecimal projectedExposure = currentMarketExposure.add(newTradeValue);
        BigDecimal exposurePct = projectedExposure.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);

        if (exposurePct.compareTo(MAX_GEOGRAPHIC_EXPOSURE_PCT) > 0) {
            String marketName = market == StrategyMarket.NGX ? "NGX" : "US";
            log.warn("Geographic exposure check FAILED: {} exposure would be {}% (max {}%)",
                    marketName, exposurePct, MAX_GEOGRAPHIC_EXPOSURE_PCT);
            return RiskCheckResult.fail(
                    "GEOGRAPHIC_EXPOSURE",
                    String.format("%s market exposure %.2f%% would exceed maximum %.2f%%",
                            marketName, exposurePct, MAX_GEOGRAPHIC_EXPOSURE_PCT),
                    List.of(String.format("market=%s, projectedExposurePct=%.4f%%, max=%.2f%%",
                            marketName, exposurePct, MAX_GEOGRAPHIC_EXPOSURE_PCT))
            );
        }

        log.debug("Geographic exposure check passed: {} at {}% (max {}%)",
                market, exposurePct, MAX_GEOGRAPHIC_EXPOSURE_PCT);
        return RiskCheckResult.pass("GEOGRAPHIC_EXPOSURE");
    }

    // ---- Private helpers ----

    /**
     * Calculates total position value for a given market.
     * Convention: symbols ending with ".XNSA" are NGX; all others are US.
     */
    private BigDecimal calculateMarketExposure(StrategyMarket market) {
        List<Position> openPositions = positionRepository.findByIsOpenTrue();

        return openPositions.stream()
                .filter(p -> isPositionInMarket(p, market))
                .map(p -> {
                    BigDecimal price = p.getCurrentPrice() != null ? p.getCurrentPrice() : p.getAvgEntryPrice();
                    return price.multiply(BigDecimal.valueOf(p.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPositionInMarket(Position position, StrategyMarket market) {
        boolean isNgx = position.getSymbol() != null && position.getSymbol().endsWith(".XNSA");
        if (market == StrategyMarket.NGX) {
            return isNgx;
        }
        return !isNgx; // US
    }

    private PortfolioSnapshot getLatestSnapshot() {
        return portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "No portfolio snapshot available. Cannot check geographic exposure."));
    }
}
