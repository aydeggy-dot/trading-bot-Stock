package com.ngxbot.signal;

import com.ngxbot.data.entity.EtfValuation;
import com.ngxbot.data.repository.EtfValuationRepository;
import com.ngxbot.signal.fundamental.NavDiscountCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NavDiscountCalculatorTest {

    @Mock
    private EtfValuationRepository etfValuationRepository;

    private NavDiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new NavDiscountCalculator(etfValuationRepository);
    }

    // ---- getPremiumDiscountPct tests ----

    @Test
    @DisplayName("Should return premium/discount percentage")
    void getPremiumDiscountPct() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(date)
                .marketPrice(new BigDecimal("900.00"))
                .nav(new BigDecimal("1000.00"))
                .premiumDiscountPct(new BigDecimal("-10.0000"))
                .build();

        when(etfValuationRepository.findBySymbolAndTradeDate("STANBICETF30", date))
                .thenReturn(Optional.of(valuation));

        BigDecimal result = calculator.getPremiumDiscountPct("STANBICETF30", date);

        assertThat(result).isEqualByComparingTo(new BigDecimal("-10.0000"));
        verify(etfValuationRepository).findBySymbolAndTradeDate("STANBICETF30", date);
    }

    @Test
    @DisplayName("Should return null when no valuation data exists")
    void noData() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(etfValuationRepository.findBySymbolAndTradeDate("UNKNOWN", date))
                .thenReturn(Optional.empty());

        assertThat(calculator.getPremiumDiscountPct("UNKNOWN", date)).isNull();
    }

    @Test
    @DisplayName("Should return positive value for ETF at premium")
    void getPremiumDiscountPct_premium() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        EtfValuation valuation = EtfValuation.builder()
                .symbol("VETGRIF30")
                .tradeDate(date)
                .marketPrice(new BigDecimal("1100.00"))
                .nav(new BigDecimal("1000.00"))
                .premiumDiscountPct(new BigDecimal("10.0000"))
                .build();

        when(etfValuationRepository.findBySymbolAndTradeDate("VETGRIF30", date))
                .thenReturn(Optional.of(valuation));

        BigDecimal result = calculator.getPremiumDiscountPct("VETGRIF30", date);

        assertThat(result).isPositive();
        assertThat(result).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    // ---- isDiscountNarrowing tests ----

    @Test
    @DisplayName("Should detect narrowing discount")
    void isDiscountNarrowing_true() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-8.0000"))
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-12.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today, yesterday));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isTrue();
    }

    @Test
    @DisplayName("Should not detect narrowing when discount is widening")
    void isDiscountNarrowing_false() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-15.0000"))
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-10.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today, yesterday));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isFalse();
    }

    @Test
    @DisplayName("Should not detect narrowing when at premium")
    void isDiscountNarrowing_atPremium() {
        EtfValuation today = EtfValuation.builder()
                .symbol("VETGRIF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("5.0000"))
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("VETGRIF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("3.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("VETGRIF30"))
                .thenReturn(List.of(today, yesterday));

        // today > yesterday is true, but yesterday is NOT < 0, so should be false
        assertThat(calculator.isDiscountNarrowing("VETGRIF30")).isFalse();
    }

    @Test
    @DisplayName("Should return false when only one valuation exists")
    void isDiscountNarrowing_singleValuation() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-8.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isFalse();
    }

    @Test
    @DisplayName("Should return false when no valuations exist")
    void isDiscountNarrowing_noData() {
        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(Collections.emptyList());

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isFalse();
    }

    @Test
    @DisplayName("Should return false when today discount is null")
    void isDiscountNarrowing_todayNull() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(null)
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-12.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today, yesterday));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isFalse();
    }

    @Test
    @DisplayName("Should return false when yesterday discount is null")
    void isDiscountNarrowing_yesterdayNull() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-8.0000"))
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(null)
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today, yesterday));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isFalse();
    }

    @Test
    @DisplayName("Should detect narrowing when discount moves from deep to slight")
    void isDiscountNarrowing_deepToSlight() {
        EtfValuation today = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-2.0000"))
                .build();
        EtfValuation yesterday = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-30.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(today, yesterday));

        assertThat(calculator.isDiscountNarrowing("STANBICETF30")).isTrue();
    }

    // ---- getDiscountRateOfChange tests ----

    @Test
    @DisplayName("Should return null for discount rate of change with insufficient data")
    void discountRateOfChange_insufficientData() {
        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(Collections.emptyList());

        assertThat(calculator.getDiscountRateOfChange("STANBICETF30", 5)).isNull();
    }

    @Test
    @DisplayName("Should return null for discount rate of change with only one valuation")
    void discountRateOfChange_singleValuation() {
        EtfValuation latest = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .premiumDiscountPct(new BigDecimal("-10.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(latest));

        assertThat(calculator.getDiscountRateOfChange("STANBICETF30", 5)).isNull();
    }

    @Test
    @DisplayName("Should calculate positive rate of change when discount narrows")
    void discountRateOfChange_narrowing() {
        // discount went from -20 to -10 over 5 days => change = +10, rate = 10/5 = 2.0
        EtfValuation latest = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 20))
                .premiumDiscountPct(new BigDecimal("-10.0000"))
                .build();
        EtfValuation earlier = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-20.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(latest, earlier));

        BigDecimal roc = calculator.getDiscountRateOfChange("STANBICETF30", 5);

        assertThat(roc).isNotNull();
        // Change = -10 - (-20) = +10, days = 20-14 = 6, rate = 10/6 = 1.6667
        assertThat(roc).isPositive();
    }

    @Test
    @DisplayName("Should calculate negative rate of change when discount widens")
    void discountRateOfChange_widening() {
        // discount went from -10 to -20 over some days => negative rate
        EtfValuation latest = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 20))
                .premiumDiscountPct(new BigDecimal("-20.0000"))
                .build();
        EtfValuation earlier = EtfValuation.builder()
                .symbol("STANBICETF30")
                .tradeDate(LocalDate.of(2026, 1, 14))
                .premiumDiscountPct(new BigDecimal("-10.0000"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("STANBICETF30"))
                .thenReturn(List.of(latest, earlier));

        BigDecimal roc = calculator.getDiscountRateOfChange("STANBICETF30", 5);

        assertThat(roc).isNotNull();
        assertThat(roc).isNegative();
    }

    // ---- getLatestValuation tests ----

    @Test
    @DisplayName("Should get latest valuation")
    void getLatestValuation() {
        EtfValuation valuation = EtfValuation.builder()
                .symbol("MERGROWTH")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .marketPrice(new BigDecimal("500.00"))
                .nav(new BigDecimal("600.00"))
                .build();

        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("MERGROWTH"))
                .thenReturn(List.of(valuation));

        Optional<EtfValuation> result = calculator.getLatestValuation("MERGROWTH");

        assertThat(result).isPresent();
        assertThat(result.get().getMarketPrice()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.get().getNav()).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    @DisplayName("Should return empty Optional when no valuations exist")
    void getLatestValuation_noData() {
        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("UNKNOWN"))
                .thenReturn(Collections.emptyList());

        Optional<EtfValuation> result = calculator.getLatestValuation("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return the most recent valuation when multiple exist")
    void getLatestValuation_multipleEntries() {
        EtfValuation latest = EtfValuation.builder()
                .symbol("MERGROWTH")
                .tradeDate(LocalDate.of(2026, 1, 20))
                .marketPrice(new BigDecimal("520.00"))
                .nav(new BigDecimal("610.00"))
                .build();
        EtfValuation older = EtfValuation.builder()
                .symbol("MERGROWTH")
                .tradeDate(LocalDate.of(2026, 1, 15))
                .marketPrice(new BigDecimal("500.00"))
                .nav(new BigDecimal("600.00"))
                .build();

        // Repository returns DESC order, so latest first
        when(etfValuationRepository.findBySymbolOrderByTradeDateDesc("MERGROWTH"))
                .thenReturn(List.of(latest, older));

        Optional<EtfValuation> result = calculator.getLatestValuation("MERGROWTH");

        assertThat(result).isPresent();
        assertThat(result.get().getTradeDate()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(result.get().getMarketPrice()).isEqualByComparingTo(new BigDecimal("520.00"));
    }
}
