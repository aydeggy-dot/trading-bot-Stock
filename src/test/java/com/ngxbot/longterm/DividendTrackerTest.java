package com.ngxbot.longterm;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.entity.DividendEvent;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import com.ngxbot.longterm.repository.DividendEventRepository;
import com.ngxbot.longterm.service.DividendTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendTrackerTest {

    @Mock private DividendEventRepository dividendEventRepository;
    @Mock private CoreHoldingRepository coreHoldingRepository;
    @Mock private LongtermProperties longtermProperties;
    @Mock private LongtermProperties.Dividend dividendProperties;

    private DividendTracker dividendTracker;

    private static final LocalDate EX_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 4, 10);

    @BeforeEach
    void setUp() {
        dividendTracker = new DividendTracker(dividendEventRepository,
                coreHoldingRepository, longtermProperties);
    }

    // ---- Helpers ----

    private CoreHolding coreHolding(String symbol, String market, int sharesHeld) {
        return CoreHolding.builder()
                .symbol(symbol)
                .market(market)
                .currency("NGX".equals(market) ? "NGN" : "USD")
                .sharesHeld(sharesHeld)
                .targetWeightPct(new BigDecimal("10.00"))
                .currentWeightPct(new BigDecimal("10.00"))
                .marketValue(new BigDecimal("100000"))
                .avgCostBasis(new BigDecimal("25.00"))
                .build();
    }

    // ---- Tests ----

    @Test
    @DisplayName("recordDividend for NGX applies no withholding tax and netAmount equals grossAmount")
    void recordDividend_ngxNonWithholdingTax() {
        String symbol = "ZENITHBANK";
        BigDecimal dps = new BigDecimal("3.50");
        int shares = 500;

        when(dividendEventRepository.findBySymbolAndExDate(symbol, EX_DATE))
                .thenReturn(Optional.empty());
        when(coreHoldingRepository.findBySymbolAndMarket(symbol, "NGX"))
                .thenReturn(Optional.of(coreHolding(symbol, "NGX", shares)));
        when(dividendEventRepository.save(any(DividendEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DividendEvent result = dividendTracker.recordDividend(symbol, "NGX", EX_DATE, PAYMENT_DATE, dps);

        // Gross = 3.50 * 500 = 1750.00
        assertThat(result.getGrossAmount()).isEqualByComparingTo(new BigDecimal("1750.00"));
        assertThat(result.getWithholdingTaxPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNetAmountReceived()).isEqualByComparingTo(result.getGrossAmount());
        assertThat(result.getCurrency()).isEqualTo("NGN");
        assertThat(result.getSharesHeldAtExDate()).isEqualTo(shares);
        assertThat(result.getReinvested()).isFalse();
    }

    @Test
    @DisplayName("recordDividend for US applies 30% withholding tax: gross=200, net=140")
    void recordDividend_usApplies30PercentWithholding() {
        String symbol = "VOO";
        BigDecimal dps = new BigDecimal("2.00");
        int shares = 100;

        when(dividendEventRepository.findBySymbolAndExDate(symbol, EX_DATE))
                .thenReturn(Optional.empty());
        when(coreHoldingRepository.findBySymbolAndMarket(symbol, "US"))
                .thenReturn(Optional.of(coreHolding(symbol, "US", shares)));
        when(longtermProperties.getDividend()).thenReturn(dividendProperties);
        when(dividendProperties.getUsWithholdingTaxPct()).thenReturn(new BigDecimal("30.0"));
        when(dividendEventRepository.save(any(DividendEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DividendEvent result = dividendTracker.recordDividend(symbol, "US", EX_DATE, PAYMENT_DATE, dps);

        // Gross = 2.00 * 100 = 200.00
        assertThat(result.getGrossAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getWithholdingTaxPct()).isEqualByComparingTo(new BigDecimal("30.0"));
        // Net = 200 * (1 - 0.30) = 200 * 0.70 = 140.00
        assertThat(result.getNetAmountReceived()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("getUpcomingExDates returns only events within the specified day range")
    void getUpcomingExDates_returnsEventsWithinRange() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(7);

        DividendEvent withinRange = DividendEvent.builder()
                .symbol("ZENITHBANK").market("NGX").currency("NGN")
                .exDate(today.plusDays(3))
                .dividendPerShare(new BigDecimal("2.00"))
                .build();

        // Mock: only the within-range event is returned by the repository
        when(dividendEventRepository.findByExDateBetween(today, horizon))
                .thenReturn(List.of(withinRange));

        List<DividendEvent> result = dividendTracker.getUpcomingExDates(7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("ZENITHBANK");
        assertThat(result.get(0).getExDate()).isBetween(today, horizon);
    }

    @Test
    @DisplayName("calculateEffectiveYield for NGX returns gross yield: price=100, dps=8 -> 8.00%")
    void calculateEffectiveYield_ngxReturnsGross() {
        String symbol = "GTCO";
        BigDecimal currentPrice = new BigDecimal("100.00");
        BigDecimal dps = new BigDecimal("8.0000");

        DividendEvent event = DividendEvent.builder()
                .symbol(symbol).market("NGX").currency("NGN")
                .exDate(EX_DATE).dividendPerShare(dps)
                .build();

        when(dividendEventRepository.findBySymbol(symbol)).thenReturn(List.of(event));

        BigDecimal yield = dividendTracker.calculateEffectiveYield(symbol, "NGX", currentPrice);

        // Gross yield = (8 / 100) * 100 = 8.00
        assertThat(yield).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    @DisplayName("calculateEffectiveYield for US returns net yield after 30% tax: price=100, dps=4 -> ~2.80%")
    void calculateEffectiveYield_usReturnsNetAfterTax() {
        String symbol = "SCHD";
        BigDecimal currentPrice = new BigDecimal("100.00");
        BigDecimal dps = new BigDecimal("4.0000");

        DividendEvent event = DividendEvent.builder()
                .symbol(symbol).market("US").currency("USD")
                .exDate(EX_DATE).dividendPerShare(dps)
                .build();

        when(dividendEventRepository.findBySymbol(symbol)).thenReturn(List.of(event));

        BigDecimal yield = dividendTracker.calculateEffectiveYield(symbol, "US", currentPrice);

        // Net yield = (4 * 0.70 / 100) * 100 = 2.80
        assertThat(yield).isEqualByComparingTo(new BigDecimal("2.80"));
    }

    @Test
    @DisplayName("getUnreinvestedDividends returns correct list from repository")
    void getUnreinvestedDividends_returnsCorrectList() {
        DividendEvent event1 = DividendEvent.builder()
                .symbol("ZENITHBANK").market("NGX").currency("NGN")
                .exDate(EX_DATE).reinvested(false)
                .dividendPerShare(new BigDecimal("3.00"))
                .grossAmount(new BigDecimal("1500.00"))
                .netAmountReceived(new BigDecimal("1500.00"))
                .build();
        DividendEvent event2 = DividendEvent.builder()
                .symbol("VOO").market("US").currency("USD")
                .exDate(EX_DATE.minusDays(30)).reinvested(false)
                .dividendPerShare(new BigDecimal("1.50"))
                .grossAmount(new BigDecimal("150.00"))
                .netAmountReceived(new BigDecimal("105.00"))
                .build();

        when(dividendEventRepository.findByReinvestedFalse()).thenReturn(List.of(event1, event2));

        List<DividendEvent> result = dividendTracker.getUnreinvestedDividends();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DividendEvent::getSymbol)
                .containsExactlyInAnyOrder("ZENITHBANK", "VOO");
        assertThat(result).allMatch(e -> !e.getReinvested());
    }

    @Test
    @DisplayName("recordDividend deduplicates by symbol and exDate, returning existing event")
    void recordDividend_deduplicatesBySymbolAndExDate() {
        String symbol = "ZENITHBANK";
        BigDecimal dps = new BigDecimal("3.50");

        DividendEvent existing = DividendEvent.builder()
                .id(42L)
                .symbol(symbol).market("NGX").currency("NGN")
                .exDate(EX_DATE).paymentDate(PAYMENT_DATE)
                .dividendPerShare(dps)
                .sharesHeldAtExDate(500)
                .grossAmount(new BigDecimal("1750.00"))
                .netAmountReceived(new BigDecimal("1750.00"))
                .reinvested(false)
                .build();

        when(dividendEventRepository.findBySymbolAndExDate(symbol, EX_DATE))
                .thenReturn(Optional.of(existing));

        // Call recordDividend twice with same symbol + exDate
        DividendEvent result = dividendTracker.recordDividend(symbol, "NGX", EX_DATE, PAYMENT_DATE, dps);

        // Should return existing event, not create a new one
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result).isSameAs(existing);

        // save should never be called since the event already exists
        verify(dividendEventRepository, never()).save(any(DividendEvent.class));
    }
}
