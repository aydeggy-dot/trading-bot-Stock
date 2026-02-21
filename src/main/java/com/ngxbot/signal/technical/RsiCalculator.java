package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Relative Strength Index (RSI) calculator.
 * RSI = 100 - (100 / (1 + RS))
 * RS = Average Gain / Average Loss over the period
 *
 * Uses Wilder's smoothing method (exponential moving average of gains/losses).
 */
@Slf4j
@Service
public class RsiCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 4;

    /**
     * Calculate RSI for a given period.
     *
     * @param bars OHLCV bars sorted by date ASCENDING (oldest first)
     * @param period RSI period (typically 14)
     * @return RSI value (0-100), or null if insufficient data
     */
    public BigDecimal calculate(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            log.debug("Insufficient data for RSI({}): need {} bars, have {}",
                    period, period + 1, bars == null ? 0 : bars.size());
            return null;
        }

        // Calculate price changes
        BigDecimal[] gains = new BigDecimal[bars.size() - 1];
        BigDecimal[] losses = new BigDecimal[bars.size() - 1];

        for (int i = 1; i < bars.size(); i++) {
            BigDecimal change = bars.get(i).getClosePrice().subtract(bars.get(i - 1).getClosePrice());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains[i - 1] = change;
                losses[i - 1] = BigDecimal.ZERO;
            } else {
                gains[i - 1] = BigDecimal.ZERO;
                losses[i - 1] = change.abs();
            }
        }

        // First average: simple average of first 'period' values
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        BigDecimal periodBd = new BigDecimal(period);

        for (int i = 0; i < period; i++) {
            avgGain = avgGain.add(gains[i]);
            avgLoss = avgLoss.add(losses[i]);
        }
        avgGain = avgGain.divide(periodBd, SCALE, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(periodBd, SCALE, RoundingMode.HALF_UP);

        // Wilder's smoothing for remaining values
        BigDecimal periodMinusOne = new BigDecimal(period - 1);
        for (int i = period; i < gains.length; i++) {
            avgGain = avgGain.multiply(periodMinusOne).add(gains[i])
                    .divide(periodBd, SCALE, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(periodMinusOne).add(losses[i])
                    .divide(periodBd, SCALE, RoundingMode.HALF_UP);
        }

        // Calculate RSI
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED; // No losses = RSI 100
        }

        BigDecimal rs = avgGain.divide(avgLoss, SCALE, RoundingMode.HALF_UP);
        BigDecimal rsi = HUNDRED.subtract(
                HUNDRED.divide(BigDecimal.ONE.add(rs), SCALE, RoundingMode.HALF_UP));

        log.debug("RSI({}) = {} (avgGain={}, avgLoss={})", period, rsi, avgGain, avgLoss);
        return rsi;
    }

    /**
     * Calculate RSI(14) — the standard period.
     */
    public BigDecimal calculateRsi14(List<OhlcvBar> bars) {
        return calculate(bars, 14);
    }
}
