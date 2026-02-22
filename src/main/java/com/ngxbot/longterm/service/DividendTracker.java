package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.entity.DividendEvent;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import com.ngxbot.longterm.repository.DividendEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tracks dividend ex-dates, withholding tax, and dividend yields for both NGX and US markets.
 * <p>
 * For US market holdings, a 30% withholding tax is applied automatically. NGX dividends
 * are recorded at gross (no withholding tax applied by default).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendTracker {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal US_WITHHOLDING_TAX_PCT = new BigDecimal("30.0");

    private final DividendEventRepository dividendEventRepository;
    private final CoreHoldingRepository coreHoldingRepository;
    private final LongtermProperties longtermProperties;

    /**
     * Records a dividend event for a symbol. Creates a {@link DividendEvent} entity,
     * looks up shares held from core holdings, applies withholding tax for US market
     * (30%), and computes gross and net amounts.
     *
     * @param symbol           the stock symbol
     * @param market           the market ("NGX" or "US")
     * @param exDate           the ex-dividend date
     * @param paymentDate      the payment date
     * @param dividendPerShare the dividend amount per share
     * @return the persisted DividendEvent
     */
    @Transactional
    public DividendEvent recordDividend(String symbol, String market, LocalDate exDate,
                                         LocalDate paymentDate, BigDecimal dividendPerShare) {

        // Check for duplicate
        Optional<DividendEvent> existing = dividendEventRepository.findBySymbolAndExDate(symbol, exDate);
        if (existing.isPresent()) {
            log.warn("Dividend event already exists for {} on ex-date {}. Skipping.", symbol, exDate);
            return existing.get();
        }

        String currency = "NGX".equals(market) ? "NGN" : "USD";

        // Look up shares held from core holdings
        int sharesHeld = coreHoldingRepository.findBySymbolAndMarket(symbol, market)
                .map(CoreHolding::getSharesHeld)
                .orElse(0);

        if (sharesHeld <= 0) {
            log.warn("No shares held for {}:{} at ex-date {}. Recording dividend with 0 shares.",
                    symbol, market, exDate);
        }

        // Calculate gross amount
        BigDecimal grossAmount = dividendPerShare.multiply(BigDecimal.valueOf(sharesHeld))
                .setScale(2, RoundingMode.HALF_UP);

        // Apply withholding tax for US market (30%), none for NGX
        BigDecimal withholdingTaxPct;
        BigDecimal netAmount;

        if ("US".equals(market)) {
            withholdingTaxPct = longtermProperties.getDividend().getUsWithholdingTaxPct();
            BigDecimal taxMultiplier = BigDecimal.ONE.subtract(
                    withholdingTaxPct.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
            netAmount = grossAmount.multiply(taxMultiplier).setScale(2, RoundingMode.HALF_UP);
            log.info("US dividend for {}: gross={} USD, withholding={}%, net={} USD",
                    symbol, grossAmount.toPlainString(), withholdingTaxPct, netAmount.toPlainString());
        } else {
            withholdingTaxPct = BigDecimal.ZERO;
            netAmount = grossAmount;
            log.info("NGX dividend for {}: gross={} NGN (no withholding tax)",
                    symbol, grossAmount.toPlainString());
        }

        DividendEvent event = DividendEvent.builder()
                .symbol(symbol)
                .market(market)
                .currency(currency)
                .exDate(exDate)
                .paymentDate(paymentDate)
                .dividendPerShare(dividendPerShare)
                .sharesHeldAtExDate(sharesHeld)
                .grossAmount(grossAmount)
                .withholdingTaxPct(withholdingTaxPct)
                .netAmountReceived(netAmount)
                .reinvested(false)
                .build();

        DividendEvent saved = dividendEventRepository.save(event);
        log.info("Recorded dividend event for {}:{} -- ex-date: {}, payment: {}, DPS: {}, "
                        + "shares: {}, gross: {}, net: {} {}",
                symbol, market, exDate, paymentDate, dividendPerShare.toPlainString(),
                sharesHeld, grossAmount.toPlainString(), netAmount.toPlainString(), currency);

        return saved;
    }

    /**
     * Returns dividend events with ex-dates occurring within the next N days from today.
     *
     * @param daysAhead number of days to look ahead
     * @return list of upcoming dividend events
     */
    public List<DividendEvent> getUpcomingExDates(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(daysAhead);

        List<DividendEvent> upcoming = dividendEventRepository.findByExDateBetween(today, horizon);
        log.info("Found {} dividend events with ex-dates between {} and {}",
                upcoming.size(), today, horizon);

        return upcoming;
    }

    /**
     * Returns dividend events that have not yet been reinvested.
     *
     * @return list of unreinvested dividend events
     */
    public List<DividendEvent> getUnreinvestedDividends() {
        List<DividendEvent> unreinvested = dividendEventRepository.findByReinvestedFalse();
        log.debug("Found {} unreinvested dividend events", unreinvested.size());
        return unreinvested;
    }

    /**
     * Calculates the effective dividend yield for a symbol based on the most recent
     * dividend per share and the current price.
     * <p>
     * For NGX: effective yield = (dividendPerShare / currentPrice) * 100 (gross yield).
     * For US: effective yield = (dividendPerShare * 0.70 / currentPrice) * 100 (net yield
     * after 30% withholding tax).
     *
     * @param symbol       the stock symbol
     * @param market       the market ("NGX" or "US")
     * @param currentPrice the current share price
     * @return the effective yield as a percentage, or BigDecimal.ZERO if no dividend data
     */
    public BigDecimal calculateEffectiveYield(String symbol, String market, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid current price for {}. Cannot calculate yield.", symbol);
            return BigDecimal.ZERO;
        }

        // Get the most recent dividend event for this symbol
        List<DividendEvent> events = dividendEventRepository.findBySymbol(symbol);
        if (events.isEmpty()) {
            log.debug("No dividend events found for {}. Yield is 0.", symbol);
            return BigDecimal.ZERO;
        }

        // Use the most recent dividend (last in list assuming chronological order)
        DividendEvent latestEvent = events.stream()
                .sorted((a, b) -> b.getExDate().compareTo(a.getExDate()))
                .findFirst()
                .orElse(null);

        if (latestEvent == null || latestEvent.getDividendPerShare() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal dps = latestEvent.getDividendPerShare();

        if ("US".equals(market)) {
            // Net yield after 30% withholding tax: dps * 0.70 / price * 100
            BigDecimal netDps = dps.multiply(new BigDecimal("0.70"));
            BigDecimal yield = netDps.divide(currentPrice, 6, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);
            log.debug("US effective yield for {}: {}% (net of 30% withholding)", symbol, yield);
            return yield;
        } else {
            // Gross yield for NGX: dps / price * 100
            BigDecimal yield = dps.divide(currentPrice, 6, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);
            log.debug("NGX gross yield for {}: {}%", symbol, yield);
            return yield;
        }
    }
}
