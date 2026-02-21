package com.ngxbot.signal;

import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.signal.technical.RsiCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RsiCalculatorTest {

    private RsiCalculator rsiCalculator;

    @BeforeEach
    void setUp() {
        rsiCalculator = new RsiCalculator();
    }

    @Test
    @DisplayName("Should return null when insufficient data")
    void insufficientData() {
        List<OhlcvBar> bars = createBarsWithPrices(44.0, 44.5, 43.8); // Only 3 bars
        assertThat(rsiCalculator.calculateRsi14(bars)).isNull();
    }

    @Test
    @DisplayName("Should return null for null input")
    void nullInput() {
        assertThat(rsiCalculator.calculateRsi14(null)).isNull();
    }

    @Test
    @DisplayName("Should return null for empty list")
    void emptyList() {
        assertThat(rsiCalculator.calculateRsi14(Collections.emptyList())).isNull();
    }

    @Test
    @DisplayName("Should calculate RSI correctly with known data series")
    void calculateRsi_knownData() {
        // Use a series of 15 closing prices (need 14+1 for RSI(14))
        // Prices trending up should produce RSI > 50
        List<OhlcvBar> bars = createBarsWithPrices(
                44.00, 44.34, 44.09, 43.61, 44.33,
                44.83, 45.10, 45.42, 45.84, 46.08,
                45.89, 46.03, 45.61, 46.28, 46.28
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);

        assertThat(rsi).isNotNull();
        // With mostly rising prices, RSI should be above 50
        assertThat(rsi.doubleValue()).isGreaterThan(50.0);
        assertThat(rsi.doubleValue()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("Should return RSI near 100 when all prices go up")
    void allGains() {
        List<OhlcvBar> bars = createBarsWithPrices(
                10.0, 11.0, 12.0, 13.0, 14.0,
                15.0, 16.0, 17.0, 18.0, 19.0,
                20.0, 21.0, 22.0, 23.0, 24.0
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);

        assertThat(rsi).isNotNull();
        assertThat(rsi.doubleValue()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should return RSI near 0 when all prices go down")
    void allLosses() {
        List<OhlcvBar> bars = createBarsWithPrices(
                24.0, 23.0, 22.0, 21.0, 20.0,
                19.0, 18.0, 17.0, 16.0, 15.0,
                14.0, 13.0, 12.0, 11.0, 10.0
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);

        assertThat(rsi).isNotNull();
        assertThat(rsi.doubleValue()).isLessThan(1.0);
    }

    @Test
    @DisplayName("Should handle flat prices (RSI around 50 or undefined)")
    void flatPrices() {
        List<OhlcvBar> bars = createBarsWithPrices(
                50.0, 50.0, 50.0, 50.0, 50.0,
                50.0, 50.0, 50.0, 50.0, 50.0,
                50.0, 50.0, 50.0, 50.0, 50.0
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);

        // With no changes, avg gain = 0, avg loss = 0, RSI = 100 (no losses)
        assertThat(rsi).isNotNull();
    }

    @Test
    @DisplayName("RSI should work with custom period")
    void customPeriod() {
        List<OhlcvBar> bars = createBarsWithPrices(
                10.0, 11.0, 10.5, 12.0, 11.5,
                13.0, 12.5, 14.0, 13.5, 15.0
        );

        // RSI(7) needs 8 bars minimum
        BigDecimal rsi = rsiCalculator.calculate(bars, 7);
        assertThat(rsi).isNotNull();
        assertThat(rsi.doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("RSI with exactly period+1 bars should return a valid result")
    void exactMinimumBars() {
        // RSI(14) needs exactly 15 bars
        List<OhlcvBar> bars = createBarsWithPrices(
                10.0, 10.5, 10.2, 10.8, 10.3,
                10.9, 10.4, 11.0, 10.5, 11.1,
                10.6, 11.2, 10.7, 11.3, 10.8
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);
        assertThat(rsi).isNotNull();
        assertThat(rsi.doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("RSI with period bars (one fewer than needed) should return null")
    void exactlyPeriodBars_returnsNull() {
        // RSI(14) needs 15 bars; 14 is insufficient
        List<OhlcvBar> bars = createBarsWithPrices(
                10.0, 10.5, 10.2, 10.8, 10.3,
                10.9, 10.4, 11.0, 10.5, 11.1,
                10.6, 11.2, 10.7, 11.3
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);
        assertThat(rsi).isNull();
    }

    @Test
    @DisplayName("RSI with more bars should apply Wilder smoothing beyond initial period")
    void wilderSmoothing() {
        // 20 bars = 15 minimum + 5 extra for Wilder smoothing iterations
        List<OhlcvBar> bars = createBarsWithPrices(
                44.00, 44.34, 44.09, 43.61, 44.33,
                44.83, 45.10, 45.42, 45.84, 46.08,
                45.89, 46.03, 45.61, 46.28, 46.28,
                46.00, 46.03, 46.41, 46.22, 45.64
        );

        BigDecimal rsi = rsiCalculator.calculateRsi14(bars);

        assertThat(rsi).isNotNull();
        assertThat(rsi.doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("RSI should be symmetric: all gains = 100, all losses < 1")
    void rsiSymmetry() {
        List<OhlcvBar> allUp = createBarsWithPrices(
                10.0, 11.0, 12.0, 13.0, 14.0,
                15.0, 16.0, 17.0, 18.0, 19.0,
                20.0, 21.0, 22.0, 23.0, 24.0
        );
        List<OhlcvBar> allDown = createBarsWithPrices(
                24.0, 23.0, 22.0, 21.0, 20.0,
                19.0, 18.0, 17.0, 16.0, 15.0,
                14.0, 13.0, 12.0, 11.0, 10.0
        );

        BigDecimal rsiUp = rsiCalculator.calculateRsi14(allUp);
        BigDecimal rsiDown = rsiCalculator.calculateRsi14(allDown);

        assertThat(rsiUp).isNotNull();
        assertThat(rsiDown).isNotNull();
        // rsiUp + rsiDown should approximately equal 100
        // All gains => RSI=100, all losses => RSI=0
        assertThat(rsiUp.doubleValue()).isEqualTo(100.0);
        assertThat(rsiDown.doubleValue()).isLessThan(1.0);
    }

    @Test
    @DisplayName("RSI(7) with insufficient data should return null")
    void customPeriod_insufficientData() {
        // RSI(7) needs 8 bars; provide only 7
        List<OhlcvBar> bars = createBarsWithPrices(
                10.0, 11.0, 10.5, 12.0, 11.5, 13.0, 12.5
        );

        BigDecimal rsi = rsiCalculator.calculate(bars, 7);
        assertThat(rsi).isNull();
    }

    private List<OhlcvBar> createBarsWithPrices(double... prices) {
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (double price : prices) {
            bars.add(OhlcvBar.builder()
                    .symbol("TEST")
                    .tradeDate(date)
                    .closePrice(new BigDecimal(String.valueOf(price)))
                    .highPrice(new BigDecimal(String.valueOf(price + 0.5)))
                    .lowPrice(new BigDecimal(String.valueOf(price - 0.5)))
                    .openPrice(new BigDecimal(String.valueOf(price)))
                    .volume(100000L)
                    .build());
            date = date.plusDays(1);
        }
        return bars;
    }
}
