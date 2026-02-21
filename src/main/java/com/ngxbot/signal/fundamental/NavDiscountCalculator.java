package com.ngxbot.signal.fundamental;

import com.ngxbot.data.entity.EtfValuation;
import com.ngxbot.data.repository.EtfValuationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Computes ETF premium/discount to NAV and rate of change.
 * The primary trading edge: NGX ETFs routinely trade at 10-50% premium or discount to NAV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavDiscountCalculator {

    private static final int SCALE = 4;

    private final EtfValuationRepository etfValuationRepository;

    /**
     * Get the current premium/discount percentage for an ETF.
     * Negative = discount (buy signal), Positive = premium (potential sell).
     *
     * @param symbol ETF symbol
     * @param date date to check
     * @return premium/discount percentage, or null if no data
     */
    public BigDecimal getPremiumDiscountPct(String symbol, LocalDate date) {
        return etfValuationRepository.findBySymbolAndTradeDate(symbol, date)
                .map(EtfValuation::getPremiumDiscountPct)
                .orElse(null);
    }

    /**
     * Get the rate of change of the discount over the last N days.
     * Positive rate = discount is narrowing (bullish signal).
     * Negative rate = discount is widening (bearish or deeper value).
     *
     * @param symbol ETF symbol
     * @param days number of days to look back
     * @return rate of change in percentage points per day, or null if insufficient data
     */
    public BigDecimal getDiscountRateOfChange(String symbol, int days) {
        List<EtfValuation> valuations = etfValuationRepository
                .findBySymbolOrderByTradeDateDesc(symbol);

        if (valuations.size() < 2) return null;

        // Get most recent and N-days-ago valuation
        EtfValuation latest = valuations.get(0);
        EtfValuation earlier = null;

        for (EtfValuation v : valuations) {
            if (v.getTradeDate().isBefore(latest.getTradeDate().minusDays(days - 1))) {
                earlier = v;
                break;
            }
        }

        if (earlier == null || latest.getPremiumDiscountPct() == null || earlier.getPremiumDiscountPct() == null) {
            return null;
        }

        // Rate of change = (latest discount - earlier discount) / days
        long actualDays = latest.getTradeDate().toEpochDay() - earlier.getTradeDate().toEpochDay();
        if (actualDays == 0) return null;

        BigDecimal change = latest.getPremiumDiscountPct().subtract(earlier.getPremiumDiscountPct());
        return change.divide(new BigDecimal(actualDays), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Check if the discount is narrowing (yesterday's discount was larger than today's).
     * This is a STRONG BUY signal per the ETF NAV Arbitrage strategy.
     */
    public boolean isDiscountNarrowing(String symbol) {
        List<EtfValuation> valuations = etfValuationRepository
                .findBySymbolOrderByTradeDateDesc(symbol);

        if (valuations.size() < 2) return false;

        BigDecimal today = valuations.get(0).getPremiumDiscountPct();
        BigDecimal yesterday = valuations.get(1).getPremiumDiscountPct();

        if (today == null || yesterday == null) return false;

        // Both should be negative (discount), and today less negative than yesterday
        return today.compareTo(yesterday) > 0 && yesterday.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get the latest NAV for an ETF.
     */
    public Optional<EtfValuation> getLatestValuation(String symbol) {
        List<EtfValuation> valuations = etfValuationRepository
                .findBySymbolOrderByTradeDateDesc(symbol);
        return valuations.isEmpty() ? Optional.empty() : Optional.of(valuations.get(0));
    }
}
