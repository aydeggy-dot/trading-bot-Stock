package com.ngxbot.signal;

import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.signal.technical.MacdCalculator;
import com.ngxbot.signal.technical.MovingAverageCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MacdCalculatorTest {

    private MacdCalculator macdCalculator;

    @BeforeEach
    void setUp() {
        MovingAverageCalculator maCalc = new MovingAverageCalculator();
        macdCalculator = new MacdCalculator(maCalc);
    }

    @Test
    @DisplayName("Should return null when insufficient data for MACD")
    void insufficientData() {
        List<OhlcvBar> bars = createBarsWithPrices(20); // Need 35+
        assertThat(macdCalculator.calculate(bars)).isNull();
    }

    @Test
    @DisplayName("Should return null for null input")
    void nullInput() {
        assertThat(macdCalculator.calculate(null)).isNull();
    }

    @Test
    @DisplayName("Should return null for empty list")
    void emptyList() {
        assertThat(macdCalculator.calculate(Collections.emptyList())).isNull();
    }

    @Test
    @DisplayName("Should calculate MACD with all three components")
    void calculateMacd_fullResult() {
        // Create 40 bars with an uptrend -- enough for MACD(12,26,9)
        List<OhlcvBar> bars = createUptrend(40, 50.0, 0.5);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.macdLine()).isNotNull();
        assertThat(result.signalLine()).isNotNull();
        assertThat(result.histogram()).isNotNull();
    }

    @Test
    @DisplayName("MACD line should be positive in uptrend (EMA12 > EMA26)")
    void macdPositiveInUptrend() {
        List<OhlcvBar> bars = createUptrend(50, 100.0, 1.0);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.macdLine()).isPositive();
    }

    @Test
    @DisplayName("MACD line should be negative in downtrend (EMA12 < EMA26)")
    void macdNegativeInDowntrend() {
        List<OhlcvBar> bars = createDowntrend(50, 200.0, 1.0);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.macdLine()).isNegative();
    }

    @Test
    @DisplayName("Histogram should equal MACD line minus signal line")
    void histogramEqualsLineMinusSignal() {
        List<OhlcvBar> bars = createUptrend(45, 50.0, 0.3);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        BigDecimal expected = result.macdLine().subtract(result.signalLine());
        assertThat(result.histogram()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Should return null with exactly 34 bars (one less than minimum)")
    void exactlyBelowMinimum() {
        List<OhlcvBar> bars = createUptrend(34, 50.0, 0.5);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return valid result with exactly 35 bars (minimum)")
    void exactMinimumBars() {
        List<OhlcvBar> bars = createUptrend(35, 50.0, 0.5);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.macdLine()).isNotNull();
        assertThat(result.signalLine()).isNotNull();
        assertThat(result.histogram()).isNotNull();
    }

    @Test
    @DisplayName("MACD near zero for flat prices")
    void flatPrices_macdNearZero() {
        // All close prices identical => EMA12 == EMA26 => MACD ~= 0
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 40; i++) {
            bars.add(buildBar(date, 100.0));
            date = date.plusDays(1);
        }

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.macdLine().abs().doubleValue()).isLessThan(0.01);
        assertThat(result.signalLine().abs().doubleValue()).isLessThan(0.01);
        assertThat(result.histogram().abs().doubleValue()).isLessThan(0.01);
    }

    @Test
    @DisplayName("MACD magnitude should increase with steeper uptrend")
    void steepUptrendHasLargerMacd() {
        List<OhlcvBar> gentle = createUptrend(50, 100.0, 0.5);
        List<OhlcvBar> steep = createUptrend(50, 100.0, 2.0);

        MacdCalculator.MacdResult gentleResult = macdCalculator.calculate(gentle);
        MacdCalculator.MacdResult steepResult = macdCalculator.calculate(steep);

        assertThat(gentleResult).isNotNull();
        assertThat(steepResult).isNotNull();
        // Steeper uptrend should produce a larger MACD line
        assertThat(steepResult.macdLine().abs()).isGreaterThan(gentleResult.macdLine().abs());
    }

    @Test
    @DisplayName("Signal line and histogram should exist when data is sufficient")
    void signalAndHistogramPresent() {
        List<OhlcvBar> bars = createUptrend(60, 50.0, 0.3);

        MacdCalculator.MacdResult result = macdCalculator.calculate(bars);

        assertThat(result).isNotNull();
        assertThat(result.signalLine()).isNotNull();
        assertThat(result.histogram()).isNotNull();
    }

    @Test
    @DisplayName("Downtrend MACD magnitude should increase with steeper decline")
    void steepDowntrendHasLargerNegativeMacd() {
        List<OhlcvBar> gentle = createDowntrend(50, 200.0, 0.5);
        List<OhlcvBar> steep = createDowntrend(50, 200.0, 2.0);

        MacdCalculator.MacdResult gentleResult = macdCalculator.calculate(gentle);
        MacdCalculator.MacdResult steepResult = macdCalculator.calculate(steep);

        assertThat(gentleResult).isNotNull();
        assertThat(steepResult).isNotNull();
        // Both should be negative
        assertThat(gentleResult.macdLine()).isNegative();
        assertThat(steepResult.macdLine()).isNegative();
        // Steeper downtrend should produce a more negative MACD
        assertThat(steepResult.macdLine()).isLessThan(gentleResult.macdLine());
    }

    private List<OhlcvBar> createBarsWithPrices(int count) {
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = 50.0 + (i * 0.1);
            bars.add(buildBar(date, price));
            date = date.plusDays(1);
        }
        return bars;
    }

    private List<OhlcvBar> createUptrend(int count, double startPrice, double dailyGain) {
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = startPrice + (i * dailyGain);
            bars.add(buildBar(date, price));
            date = date.plusDays(1);
        }
        return bars;
    }

    private List<OhlcvBar> createDowntrend(int count, double startPrice, double dailyLoss) {
        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = startPrice - (i * dailyLoss);
            bars.add(buildBar(date, price));
            date = date.plusDays(1);
        }
        return bars;
    }

    private OhlcvBar buildBar(LocalDate date, double price) {
        return OhlcvBar.builder()
                .symbol("TEST")
                .tradeDate(date)
                .closePrice(new BigDecimal(String.valueOf(price)))
                .highPrice(new BigDecimal(String.valueOf(price + 1.0)))
                .lowPrice(new BigDecimal(String.valueOf(price - 1.0)))
                .openPrice(new BigDecimal(String.valueOf(price)))
                .volume(100000L)
                .build();
    }
}
