package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.EtfValuation;
import com.ngxbot.signal.fundamental.NavDiscountCalculator;
import com.ngxbot.signal.model.IndicatorSnapshot;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.technical.TechnicalIndicatorService;
import com.ngxbot.signal.technical.VolumeAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtfNavArbitrageStrategyTest {

    @Mock private TechnicalIndicatorService technicalIndicatorService;
    @Mock private NavDiscountCalculator navDiscountCalculator;
    @Mock private VolumeAnalyzer volumeAnalyzer;

    private EtfNavArbitrageStrategy strategy;
    private StrategyProperties strategyProperties;
    private TradingProperties tradingProperties;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        strategyProperties = new StrategyProperties();
        tradingProperties = new TradingProperties();
        tradingProperties.setWatchlist(new TradingProperties.Watchlist());
        tradingProperties.getWatchlist().setEtfs(List.of("STANBICETF30", "VETGRIF30"));

        strategy = new EtfNavArbitrageStrategy(
                strategyProperties, tradingProperties,
                technicalIndicatorService, navDiscountCalculator, volumeAnalyzer
        );
    }

    @Test
    @DisplayName("Should generate BUY signal when ETF has >10% discount, RSI<60, volume>1.2x")
    void buySignal_allConditionsMet() {
        // ETF at 15% discount
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30").tradeDate(TEST_DATE)
                .marketPrice(new BigDecimal("850")).nav(new BigDecimal("1000"))
                .premiumDiscountPct(new BigDecimal("-15.0000"))
                .build();
        when(navDiscountCalculator.getLatestValuation("STANBICETF30"))
                .thenReturn(Optional.of(valuation));
        when(navDiscountCalculator.isDiscountNarrowing("STANBICETF30")).thenReturn(false);

        // Indicators: RSI=45, volume ratio=1.5
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("45"), null, null, null,
                new BigDecimal("900"), null, null, new BigDecimal("20"),
                new BigDecimal("1.5"), null, new BigDecimal("850"), 150000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("STANBICETF30", TEST_DATE)).thenReturn(snapshot);
        when(technicalIndicatorService.getBars(anyString(), anyInt())).thenReturn(List.of());

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);

        assertThat(signals).isNotEmpty();
        TradeSignal buy = signals.stream().filter(s -> s.side() == TradeSide.BUY).findFirst().orElse(null);
        assertThat(buy).isNotNull();
        assertThat(buy.strength()).isEqualTo(SignalStrength.BUY);
        assertThat(buy.strategy()).isEqualTo("ETF_NAV_ARBITRAGE");
        assertThat(buy.stopLoss()).isNotNull();
        assertThat(buy.confidenceScore()).isGreaterThan(50);
    }

    @Test
    @DisplayName("Should generate STRONG_BUY when discount is narrowing")
    void strongBuy_discountNarrowing() {
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30").tradeDate(TEST_DATE)
                .marketPrice(new BigDecimal("850")).nav(new BigDecimal("1000"))
                .premiumDiscountPct(new BigDecimal("-15.0000"))
                .build();
        when(navDiscountCalculator.getLatestValuation("STANBICETF30"))
                .thenReturn(Optional.of(valuation));
        when(navDiscountCalculator.isDiscountNarrowing("STANBICETF30")).thenReturn(true);

        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("45"), null, null, null,
                new BigDecimal("900"), null, null, new BigDecimal("20"),
                new BigDecimal("1.5"), null, new BigDecimal("850"), 150000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("STANBICETF30", TEST_DATE)).thenReturn(snapshot);
        when(technicalIndicatorService.getBars(anyString(), anyInt())).thenReturn(List.of());

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);

        TradeSignal buy = signals.stream().filter(s -> s.side() == TradeSide.BUY).findFirst().orElse(null);
        assertThat(buy).isNotNull();
        assertThat(buy.strength()).isEqualTo(SignalStrength.STRONG_BUY);
    }

    @Test
    @DisplayName("Should NOT generate signal when discount is less than 10%")
    void noSignal_insufficientDiscount() {
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30").tradeDate(TEST_DATE)
                .marketPrice(new BigDecimal("950")).nav(new BigDecimal("1000"))
                .premiumDiscountPct(new BigDecimal("-5.0000"))
                .build();
        when(navDiscountCalculator.getLatestValuation("STANBICETF30"))
                .thenReturn(Optional.of(valuation));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("45"), null, null, null,
                new BigDecimal("900"), null, null, new BigDecimal("20"),
                new BigDecimal("1.5"), null, new BigDecimal("950"), 150000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("STANBICETF30", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);

        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when RSI is too high")
    void noSignal_rsiTooHigh() {
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30").tradeDate(TEST_DATE)
                .marketPrice(new BigDecimal("850")).nav(new BigDecimal("1000"))
                .premiumDiscountPct(new BigDecimal("-15.0000"))
                .build();
        when(navDiscountCalculator.getLatestValuation("STANBICETF30"))
                .thenReturn(Optional.of(valuation));

        // RSI = 70 (>= 60 threshold)
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("70"), null, null, null,
                new BigDecimal("900"), null, null, new BigDecimal("20"),
                new BigDecimal("1.5"), null, new BigDecimal("850"), 150000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("STANBICETF30", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);

        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when volume is too low")
    void noSignal_volumeTooLow() {
        EtfValuation valuation = EtfValuation.builder()
                .symbol("STANBICETF30").tradeDate(TEST_DATE)
                .marketPrice(new BigDecimal("850")).nav(new BigDecimal("1000"))
                .premiumDiscountPct(new BigDecimal("-15.0000"))
                .build();
        when(navDiscountCalculator.getLatestValuation("STANBICETF30"))
                .thenReturn(Optional.of(valuation));

        // Volume ratio 0.8 (< 1.2 threshold)
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("45"), null, null, null,
                new BigDecimal("900"), null, null, new BigDecimal("20"),
                new BigDecimal("0.8"), null, new BigDecimal("850"), 80000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("STANBICETF30", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);

        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when no NAV data available")
    void noSignal_noNavData() {
        when(navDiscountCalculator.getLatestValuation("STANBICETF30")).thenReturn(Optional.empty());

        List<TradeSignal> signals = strategy.evaluate("STANBICETF30", TEST_DATE);
        assertThat(signals).isEmpty();
    }

    @Test
    @DisplayName("Strategy name should be ETF_NAV_ARBITRAGE")
    void strategyName() {
        assertThat(strategy.getName()).isEqualTo("ETF_NAV_ARBITRAGE");
    }

    @Test
    @DisplayName("Target symbols should be ETF list from config")
    void targetSymbols() {
        assertThat(strategy.getTargetSymbols()).containsExactly("STANBICETF30", "VETGRIF30");
    }
}
