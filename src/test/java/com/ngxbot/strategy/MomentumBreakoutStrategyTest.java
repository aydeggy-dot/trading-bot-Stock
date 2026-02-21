package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.OhlcvBar;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MomentumBreakoutStrategyTest {

    @Mock private TechnicalIndicatorService technicalIndicatorService;
    @Mock private VolumeAnalyzer volumeAnalyzer;

    private MomentumBreakoutStrategy strategy;
    private StrategyProperties strategyProperties;
    private TradingProperties tradingProperties;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 20);

    @BeforeEach
    void setUp() {
        strategyProperties = new StrategyProperties();
        tradingProperties = new TradingProperties();
        tradingProperties.setWatchlist(new TradingProperties.Watchlist());
        tradingProperties.getWatchlist().setLargeCaps(List.of("ZENITHBANK", "GTCO"));
        tradingProperties.getWatchlist().setEtfs(List.of("STANBICETF30"));

        strategy = new MomentumBreakoutStrategy(
                strategyProperties, tradingProperties,
                technicalIndicatorService, volumeAnalyzer
        );
    }

    @Test
    @DisplayName("Should generate BUY when all momentum conditions met")
    void buySignal_allConditionsMet() {
        // Volume 4x, price above SMA20, RSI=55, consolidating, enough liquidity
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("55"), new BigDecimal("1.5"), new BigDecimal("1.0"),
                new BigDecimal("0.5"), new BigDecimal("40.00"), null, null,
                new BigDecimal("2.0"), new BigDecimal("4.0"), null,
                new BigDecimal("45.00"), 400000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<OhlcvBar> bars = createBars(30, 40.0, 0.1);
        when(technicalIndicatorService.getBars("ZENITHBANK", 30)).thenReturn(bars);
        when(volumeAnalyzer.priceRangePct(bars, 20)).thenReturn(new BigDecimal("8.0"));

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);

        TradeSignal buy = signals.stream().filter(s -> s.side() == TradeSide.BUY).findFirst().orElse(null);
        assertThat(buy).isNotNull();
        assertThat(buy.strategy()).isEqualTo("MOMENTUM_BREAKOUT");
        assertThat(buy.stopLoss()).isNotNull();
        assertThat(buy.target()).isNotNull();
        // Stop should be below entry, target above
        assertThat(buy.stopLoss()).isLessThan(buy.suggestedPrice());
        assertThat(buy.target()).isGreaterThan(buy.suggestedPrice());
    }

    @Test
    @DisplayName("Should NOT generate signal when volume spike insufficient")
    void noSignal_lowVolume() {
        // Volume ratio only 2.0 (need 3.0+)
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("55"), null, null, null,
                new BigDecimal("40.00"), null, null, new BigDecimal("2.0"),
                new BigDecimal("2.0"), null, new BigDecimal("45.00"), 200000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);
        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when price below SMA20")
    void noSignal_belowSma() {
        // Price 35 < SMA20 40
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("55"), null, null, null,
                new BigDecimal("40.00"), null, null, new BigDecimal("2.0"),
                new BigDecimal("4.0"), null, new BigDecimal("35.00"), 400000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);
        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when RSI outside range")
    void noSignal_rsiOutOfRange() {
        // RSI=70 (> maxRsi 65)
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("70"), null, null, null,
                new BigDecimal("40.00"), null, null, new BigDecimal("2.0"),
                new BigDecimal("4.0"), null, new BigDecimal("45.00"), 400000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);
        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when not consolidating (range > 15%)")
    void noSignal_notConsolidating() {
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("55"), null, null, null,
                new BigDecimal("40.00"), null, null, new BigDecimal("2.0"),
                new BigDecimal("4.0"), null, new BigDecimal("45.00"), 400000L, 100000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<OhlcvBar> bars = createBars(30, 40.0, 0.1);
        when(technicalIndicatorService.getBars("ZENITHBANK", 30)).thenReturn(bars);
        // Range 20% > 15% threshold
        when(volumeAnalyzer.priceRangePct(bars, 20)).thenReturn(new BigDecimal("20.0"));

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);
        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Should NOT generate signal when avg volume too low")
    void noSignal_lowLiquidity() {
        // avgVolume20d = 5000 (< 10000 minimum)
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                new BigDecimal("55"), null, null, null,
                new BigDecimal("40.00"), null, null, new BigDecimal("2.0"),
                new BigDecimal("4.0"), null, new BigDecimal("45.00"), 400000L, 5000L
        );
        when(technicalIndicatorService.computeSnapshot("ZENITHBANK", TEST_DATE)).thenReturn(snapshot);

        List<OhlcvBar> bars = createBars(30, 40.0, 0.1);
        when(technicalIndicatorService.getBars("ZENITHBANK", 30)).thenReturn(bars);
        when(volumeAnalyzer.priceRangePct(bars, 20)).thenReturn(new BigDecimal("8.0"));

        List<TradeSignal> signals = strategy.evaluate("ZENITHBANK", TEST_DATE);
        assertThat(signals.stream().filter(s -> s.side() == TradeSide.BUY).toList()).isEmpty();
    }

    @Test
    @DisplayName("Strategy name should be MOMENTUM_BREAKOUT")
    void strategyName() {
        assertThat(strategy.getName()).isEqualTo("MOMENTUM_BREAKOUT");
    }

    @Test
    @DisplayName("Target symbols should include large caps and ETFs")
    void targetSymbols() {
        assertThat(strategy.getTargetSymbols()).contains("ZENITHBANK", "GTCO", "STANBICETF30");
    }

    private List<OhlcvBar> createBars(int count, double startPrice, double increment) {
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = TEST_DATE.minusDays(count);
        for (int i = 0; i < count; i++) {
            double price = startPrice + (i * increment);
            bars.add(OhlcvBar.builder()
                    .symbol("ZENITHBANK").tradeDate(date.plusDays(i))
                    .closePrice(new BigDecimal(String.valueOf(price)))
                    .highPrice(new BigDecimal(String.valueOf(price + 0.5)))
                    .lowPrice(new BigDecimal(String.valueOf(price - 0.5)))
                    .openPrice(new BigDecimal(String.valueOf(price)))
                    .volume(100000L)
                    .build());
        }
        return bars;
    }
}
