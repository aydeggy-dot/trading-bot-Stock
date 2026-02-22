package com.ngxbot.longterm.service;

import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Screens stocks by fundamental criteria and produces composite scores.
 * <p>
 * This is a placeholder implementation that returns default scores since live
 * fundamental data feeds are not yet integrated. When a data provider is available,
 * the {@link #screenSymbol(String)} method should be updated to pull actual P/E,
 * ROE, dividend yield, revenue growth, and debt-to-equity metrics.
 * <p>
 * Current behavior: returns a default score of 50 for all symbols and logs what
 * a full implementation would do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalScreener {

    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("50");

    private final CoreHoldingRepository coreHoldingRepository;

    /**
     * Internal cache of manually set scores for testing / override purposes.
     * In production, this would be replaced by live data lookups.
     */
    private final Map<String, BigDecimal> scoreOverrides = new LinkedHashMap<>();

    /**
     * Screens the given symbol and returns a fundamental score from 0 to 100.
     * <p>
     * A full implementation would evaluate:
     * <ul>
     *   <li>Price-to-Earnings ratio (P/E) vs sector average</li>
     *   <li>Return on Equity (ROE)</li>
     *   <li>Dividend yield consistency</li>
     *   <li>Revenue growth rate (3-year CAGR)</li>
     *   <li>Debt-to-Equity ratio</li>
     *   <li>Free cash flow trend</li>
     *   <li>Earnings surprise history</li>
     * </ul>
     * <p>
     * Currently returns a default score of 50 (neutral) for all symbols unless
     * an override has been set.
     *
     * @param symbol the stock ticker symbol
     * @return fundamental score on a 0-100 scale
     */
    public BigDecimal screenSymbol(String symbol) {
        log.info("Screening fundamentals for symbol: {}", symbol);
        log.info("  [STUB] Would evaluate P/E, ROE, dividend yield, revenue growth, "
                + "debt/equity, FCF, earnings surprises for {}", symbol);
        log.info("  [STUB] Would fetch data from EODHD fundamentals API or similar provider");

        BigDecimal score = scoreOverrides.getOrDefault(symbol, DEFAULT_SCORE);

        log.info("Fundamental score for {}: {} ({})", symbol, score,
                scoreOverrides.containsKey(symbol) ? "override" : "default");

        return score;
    }

    /**
     * Returns the top-scored symbols for a given market, limited to the specified count.
     * <p>
     * Scores all core holdings in the market, sorts by score descending, and returns
     * the top N symbols.
     *
     * @param market the market identifier ("NGX" or "US")
     * @param limit  maximum number of symbols to return
     * @return list of top-scored symbols, ordered by score descending
     */
    public List<String> getTopHoldings(String market, int limit) {
        log.info("Getting top {} holdings for market {} by fundamental score", limit, market);

        List<CoreHolding> holdings = coreHoldingRepository.findByMarket(market);

        if (holdings.isEmpty()) {
            log.info("No core holdings found for market {}.", market);
            return List.of();
        }

        // Score each holding and sort
        List<String> topSymbols = holdings.stream()
                .map(h -> Map.entry(h.getSymbol(), screenSymbol(h.getSymbol())))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Top {} holdings for market {}: {}", limit, market, topSymbols);
        return topSymbols;
    }

    /**
     * Sets a score override for a symbol. Useful for testing or manual adjustments
     * until live fundamental data is available.
     *
     * @param symbol the stock symbol
     * @param score  the fundamental score (0-100)
     */
    public void setScoreOverride(String symbol, BigDecimal score) {
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Score must be between 0 and 100. Got: " + score);
        }
        scoreOverrides.put(symbol, score);
        log.info("Set fundamental score override for {}: {}", symbol, score);
    }

    /**
     * Clears all score overrides, reverting to default scores.
     */
    public void clearScoreOverrides() {
        scoreOverrides.clear();
        log.info("Cleared all fundamental score overrides. All symbols will return default score.");
    }
}
