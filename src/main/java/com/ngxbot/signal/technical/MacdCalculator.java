package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) calculator.
 * MACD Line = EMA(12) - EMA(26)
 * Signal Line = EMA(9) of MACD Line
 * Histogram = MACD Line - Signal Line
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MacdCalculator {

    private static final int SCALE = 4;
    private static final int FAST_PERIOD = 12;
    private static final int SLOW_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;

    private final MovingAverageCalculator movingAverageCalculator;

    /**
     * Calculate full MACD result: MACD line, signal line, and histogram.
     * Requires at least 26 + 9 = 35 bars for a valid signal line.
     *
     * @param bars OHLCV bars sorted ASCENDING (oldest first)
     * @return MacdResult with all three components, or null if insufficient data
     */
    public MacdResult calculate(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < SLOW_PERIOD + SIGNAL_PERIOD) {
            log.debug("Insufficient data for MACD: need {} bars, have {}",
                    SLOW_PERIOD + SIGNAL_PERIOD, bars == null ? 0 : bars.size());
            return null;
        }

        // Calculate MACD line values for enough bars to compute signal EMA(9)
        BigDecimal[] macdValues = new BigDecimal[bars.size() - SLOW_PERIOD + 1];
        for (int i = 0; i < macdValues.length; i++) {
            int end = SLOW_PERIOD + i;
            List<OhlcvBar> subset = bars.subList(0, end);
            BigDecimal ema12 = movingAverageCalculator.ema(subset, FAST_PERIOD);
            BigDecimal ema26 = movingAverageCalculator.ema(subset, SLOW_PERIOD);
            if (ema12 == null || ema26 == null) {
                macdValues[i] = BigDecimal.ZERO;
            } else {
                macdValues[i] = ema12.subtract(ema26);
            }
        }

        // Current MACD line is the last value
        BigDecimal macdLine = macdValues[macdValues.length - 1];

        // Signal line: EMA(9) of MACD values
        BigDecimal signalLine = emaOfValues(macdValues, SIGNAL_PERIOD);

        // Histogram
        BigDecimal histogram = (signalLine != null)
                ? macdLine.subtract(signalLine).setScale(SCALE, RoundingMode.HALF_UP)
                : null;

        MacdResult result = new MacdResult(
                macdLine.setScale(SCALE, RoundingMode.HALF_UP),
                signalLine != null ? signalLine.setScale(SCALE, RoundingMode.HALF_UP) : null,
                histogram
        );

        log.debug("MACD = {} | Signal = {} | Histogram = {}",
                result.macdLine(), result.signalLine(), result.histogram());
        return result;
    }

    /**
     * Calculate EMA over an array of BigDecimal values.
     */
    private BigDecimal emaOfValues(BigDecimal[] values, int period) {
        if (values.length < period) return null;

        BigDecimal multiplier = new BigDecimal("2")
                .divide(new BigDecimal(period + 1), SCALE + 4, RoundingMode.HALF_UP);

        // Seed with SMA of first 'period' values
        BigDecimal ema = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            ema = ema.add(values[i]);
        }
        ema = ema.divide(new BigDecimal(period), SCALE + 4, RoundingMode.HALF_UP);

        // Apply EMA formula
        for (int i = period; i < values.length; i++) {
            ema = values[i].subtract(ema).multiply(multiplier).add(ema)
                    .setScale(SCALE + 4, RoundingMode.HALF_UP);
        }

        return ema.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * MACD result record holding all three components.
     */
    public record MacdResult(
            BigDecimal macdLine,
            BigDecimal signalLine,
            BigDecimal histogram
    ) {}
}
