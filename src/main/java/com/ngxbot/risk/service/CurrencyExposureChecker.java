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
 * Enforces maximum 70% exposure in any single currency.
 * <p>
 * NGX positions are denominated in NGN, US positions in USD. This check
 * prevents over-concentration in one currency when the portfolio spans
 * multiple markets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExposureChecker {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_CURRENCY_EXPOSURE_PCT = new BigDecimal("70.0");

    private final PositionRepository positionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * Checks whether adding a new trade in the given market would push
     * the corresponding currency exposure above 70%.
     *
     * @param market        the market of the proposed trade (determines currency)
     * @param newTradeValue the value of the proposed trade in its native currency
     * @return risk check result indicating pass or fail
     */
    public RiskCheckResult checkCurrencyExposure(StrategyMarket market, BigDecimal newTradeValue) {
        PortfolioSnapshot snapshot = getLatestSnapshot();
        BigDecimal portfolioValue = snapshot.getTotalValue();

        if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.fail("CURRENCY_EXPOSURE",
                    "Portfolio value is zero or negative",
                    List.of("portfolioValue=" + portfolioValue));
        }

        String tradeCurrency = resolveCurrency(market);

        // Calculate current exposure in the trade's currency
        BigDecimal currentExposure = calculateCurrencyExposure(market);

        // Projected exposure after the new trade
        BigDecimal projectedExposure = currentExposure.add(newTradeValue);
        BigDecimal exposurePct = projectedExposure.multiply(HUNDRED)
                .divide(portfolioValue, 4, RoundingMode.HALF_UP);

        if (exposurePct.compareTo(MAX_CURRENCY_EXPOSURE_PCT) > 0) {
            log.warn("Currency exposure check FAILED: {} exposure would be {}% (max {}%)",
                    tradeCurrency, exposurePct, MAX_CURRENCY_EXPOSURE_PCT);
            return RiskCheckResult.fail(
                    "CURRENCY_EXPOSURE",
                    String.format("%s exposure %.2f%% would exceed maximum %.2f%%",
                            tradeCurrency, exposurePct, MAX_CURRENCY_EXPOSURE_PCT),
                    List.of(String.format("currency=%s, projectedExposurePct=%.4f%%, max=%.2f%%",
                            tradeCurrency, exposurePct, MAX_CURRENCY_EXPOSURE_PCT))
            );
        }

        log.debug("Currency exposure check passed: {} at {}% (max {}%)",
                tradeCurrency, exposurePct, MAX_CURRENCY_EXPOSURE_PCT);
        return RiskCheckResult.pass("CURRENCY_EXPOSURE");
    }

    // ---- Private helpers ----

    private BigDecimal calculateCurrencyExposure(StrategyMarket market) {
        List<Position> openPositions = positionRepository.findByIsOpenTrue();

        return openPositions.stream()
                .filter(p -> isPositionInMarket(p, market))
                .map(p -> {
                    BigDecimal price = p.getCurrentPrice() != null ? p.getCurrentPrice() : p.getAvgEntryPrice();
                    return price.multiply(BigDecimal.valueOf(p.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Determines if a position belongs to the given market.
     * <p>
     * Convention: NGX symbols end with ".XNSA" suffix (EODHD format).
     * All other symbols are assumed to be US-listed.
     */
    private boolean isPositionInMarket(Position position, StrategyMarket market) {
        if (market == StrategyMarket.BOTH) {
            return true;
        }
        boolean isNgx = position.getSymbol() != null && position.getSymbol().endsWith(".XNSA");
        if (market == StrategyMarket.NGX) {
            return isNgx;
        }
        return !isNgx; // US
    }

    private String resolveCurrency(StrategyMarket market) {
        return switch (market) {
            case NGX -> "NGN";
            case US -> "USD";
            case BOTH -> "MULTI";
        };
    }

    private PortfolioSnapshot getLatestSnapshot() {
        return portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "No portfolio snapshot available. Cannot check currency exposure."));
    }
}
