package com.ngxbot.strategy;

import com.ngxbot.config.StrategyProperties;
import com.ngxbot.signal.fundamental.DividendProximityScanner;
import com.ngxbot.signal.fundamental.PencomEligibilityChecker;
import com.ngxbot.signal.model.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pension Flow Overlay Strategy.
 *
 * NOT a trade-entry strategy. Adjusts position size limits and signal confidence.
 *
 * RULES:
 *   - NGX30 (pension-eligible): allow max 15% position
 *   - Non-pension small/mid-cap: cap at 5% position
 *   - Banking sector: allow up to 40% sector exposure
 *   - Other sectors: cap at 35% sector exposure
 *
 * CALENDAR ADJUSTMENTS:
 *   - January-February: increase equity allocation by 10% (pension inflows)
 *   - 4-6 weeks before bank dividend announcements: boost bank signals
 *   - November-December: lean into year's outperformers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PensionFlowOverlay implements Strategy {

    private static final String STRATEGY_NAME = "PENSION_FLOW_OVERLAY";

    private final StrategyProperties strategyProperties;
    private final PencomEligibilityChecker pencomEligibilityChecker;
    private final DividendProximityScanner dividendProximityScanner;

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getPensionFlow().isEnabled();
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        // This strategy doesn't generate signals — it only adjusts other strategies
        return List.of();
    }

    @Override
    public List<String> getTargetSymbols() {
        return List.of(); // Not a signal-generating strategy
    }

    /**
     * Get the maximum position size percentage for a symbol.
     *
     * @param symbol stock symbol
     * @return max position size as percentage of portfolio (e.g., 15.0 = 15%)
     */
    public BigDecimal getMaxPositionPct(String symbol) {
        if (pencomEligibilityChecker.isNgx30Member(symbol)) {
            return new BigDecimal("15.0");
        }
        return new BigDecimal("5.0");
    }

    /**
     * Get the maximum sector exposure percentage.
     *
     * @param symbol stock symbol
     * @return max sector exposure as percentage (e.g., 40.0 = 40%)
     */
    public BigDecimal getMaxSectorExposurePct(String symbol) {
        String sector = pencomEligibilityChecker.getSector(symbol);
        if (sector != null && sector.equalsIgnoreCase("Financial Services")) {
            return new BigDecimal("40.0");
        }
        return new BigDecimal("35.0");
    }

    /**
     * Get a confidence adjustment based on calendar effects and pension flows.
     * Returns a multiplier (e.g., 1.1 = 10% boost, 0.9 = 10% penalty).
     *
     * @param symbol stock symbol
     * @param date evaluation date
     * @return confidence multiplier
     */
    public BigDecimal getConfidenceMultiplier(String symbol, LocalDate date) {
        BigDecimal multiplier = BigDecimal.ONE;
        int month = date.getMonthValue();

        // January-February: pension fund inflows boost
        if (month == 1 || month == 2) {
            if (pencomEligibilityChecker.isPensionEligible(symbol)) {
                multiplier = multiplier.add(new BigDecimal("0.10"));
                log.debug("{}: Jan-Feb pension inflow boost (+10%)", symbol);
            }
        }

        // November-December: year-end positioning
        if (month == 11 || month == 12) {
            multiplier = multiplier.add(new BigDecimal("0.05"));
            log.debug("{}: Nov-Dec year-end boost (+5%)", symbol);
        }

        // Bank dividend season boost (Feb-April)
        String sector = pencomEligibilityChecker.getSector(symbol);
        if (sector != null && sector.equalsIgnoreCase("Financial Services")
                && dividendProximityScanner.isBankDividendSeason()) {
            multiplier = multiplier.add(new BigDecimal("0.10"));
            log.debug("{}: Bank dividend season boost (+10%)", symbol);
        }

        // Upcoming dividend boost
        if (dividendProximityScanner.hasUpcomingDividend(symbol, 42)) { // 6 weeks
            multiplier = multiplier.add(new BigDecimal("0.05"));
            log.debug("{}: Upcoming dividend within 6 weeks (+5%)", symbol);
        }

        return multiplier;
    }

    /**
     * Adjust a trade signal's confidence score based on pension flow factors.
     *
     * @param signal original trade signal
     * @return adjusted confidence score (capped at 100)
     */
    public int adjustConfidence(TradeSignal signal) {
        BigDecimal multiplier = getConfidenceMultiplier(signal.symbol(), signal.signalDate());
        int adjusted = new BigDecimal(signal.confidenceScore())
                .multiply(multiplier)
                .intValue();
        return Math.min(adjusted, 100);
    }
}
