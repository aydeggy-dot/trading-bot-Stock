package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Moving Average calculator: SMA (Simple) and EMA (Exponential).
 */
@Slf4j
@Service
public class MovingAverageCalculator {

    private static final int SCALE = 4;

    /**
     * Calculate Simple Moving Average (SMA) over the last N bars.
     *
     * @param bars OHLCV bars sorted ASCENDING (oldest first)
     * @param period number of bars to average
     * @return SMA value, or null if insufficient data
     */
    public BigDecimal sma(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int start = bars.size() - period;
        for (int i = start; i < bars.size(); i++) {
            sum = sum.add(bars.get(i).getClosePrice());
        }

        return sum.divide(new BigDecimal(period), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate SMA(20) — the standard short-term trend indicator.
     */
    public BigDecimal sma20(List<OhlcvBar> bars) {
        return sma(bars, 20);
    }

    /**
     * Calculate Exponential Moving Average (EMA) over the last N bars.
     * EMA uses a smoothing factor (multiplier): k = 2 / (period + 1)
     * EMA_today = (close - EMA_yesterday) * k + EMA_yesterday
     *
     * @param bars OHLCV bars sorted ASCENDING (oldest first)
     * @param period EMA period
     * @return EMA value, or null if insufficient data
     */
    public BigDecimal ema(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period) {
            return null;
        }

        // Multiplier: 2 / (period + 1)
        BigDecimal multiplier = new BigDecimal("2")
                .divide(new BigDecimal(period + 1), SCALE + 4, RoundingMode.HALF_UP);

        // Seed EMA with SMA of first 'period' bars
        BigDecimal ema = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            ema = ema.add(bars.get(i).getClosePrice());
        }
        ema = ema.divide(new BigDecimal(period), SCALE + 4, RoundingMode.HALF_UP);

        // Apply EMA formula for remaining bars
        for (int i = period; i < bars.size(); i++) {
            BigDecimal close = bars.get(i).getClosePrice();
            ema = close.subtract(ema).multiply(multiplier).add(ema)
                    .setScale(SCALE + 4, RoundingMode.HALF_UP);
        }

        return ema.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate EMA(12) — the fast MACD line component.
     */
    public BigDecimal ema12(List<OhlcvBar> bars) {
        return ema(bars, 12);
    }

    /**
     * Calculate EMA(26) — the slow MACD line component.
     */
    public BigDecimal ema26(List<OhlcvBar> bars) {
        return ema(bars, 26);
    }
}
