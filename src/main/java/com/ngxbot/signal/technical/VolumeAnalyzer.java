package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Volume analysis: volume ratio, average daily volume, and On-Balance Volume (OBV).
 */
@Slf4j
@Service
public class VolumeAnalyzer {

    private static final int SCALE = 4;

    /**
     * Calculate volume ratio: today's volume / average volume over last N days.
     * A ratio > 1.0 means above-average volume.
     *
     * @param bars OHLCV bars sorted ASCENDING (oldest first)
     * @param period number of days for average (typically 20)
     * @return volume ratio, or null if insufficient data
     */
    public BigDecimal volumeRatio(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            return null;
        }

        Long todayVolume = bars.get(bars.size() - 1).getVolume();
        if (todayVolume == null || todayVolume == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgVolume = averageVolume(bars, period);
        if (avgVolume == null || avgVolume.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return new BigDecimal(todayVolume)
                .divide(avgVolume, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate volume ratio using 20-day average (standard).
     */
    public BigDecimal volumeRatio20d(List<OhlcvBar> bars) {
        return volumeRatio(bars, 20);
    }

    /**
     * Calculate average daily volume over the last N bars (excluding the most recent).
     *
     * @param bars OHLCV bars sorted ASCENDING
     * @param period number of days to average
     * @return average volume, or null if insufficient data
     */
    public BigDecimal averageVolume(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            return null;
        }

        long sum = 0;
        int count = 0;
        // Average over the N bars BEFORE the most recent bar
        int start = bars.size() - 1 - period;
        for (int i = start; i < bars.size() - 1; i++) {
            Long vol = bars.get(i).getVolume();
            if (vol != null) {
                sum += vol;
                count++;
            }
        }

        if (count == 0) return null;
        return new BigDecimal(sum).divide(new BigDecimal(count), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the 20-day average daily volume (for liquidity checks).
     */
    public BigDecimal averageVolume20d(List<OhlcvBar> bars) {
        return averageVolume(bars, 20);
    }

    /**
     * Calculate On-Balance Volume (OBV).
     * OBV adds volume on up-days and subtracts on down-days.
     *
     * @param bars OHLCV bars sorted ASCENDING (oldest first)
     * @return current OBV value, or null if insufficient data
     */
    public BigDecimal obv(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) {
            return null;
        }

        long obv = 0;
        for (int i = 1; i < bars.size(); i++) {
            BigDecimal currentClose = bars.get(i).getClosePrice();
            BigDecimal prevClose = bars.get(i - 1).getClosePrice();
            Long volume = bars.get(i).getVolume();

            if (currentClose == null || prevClose == null || volume == null) continue;

            int comparison = currentClose.compareTo(prevClose);
            if (comparison > 0) {
                obv += volume;
            } else if (comparison < 0) {
                obv -= volume;
            }
            // If prices are equal, OBV stays the same
        }

        return new BigDecimal(obv);
    }

    /**
     * Check if volume has been declining for N consecutive days.
     * Used by exit strategies (3 consecutive days below 50% avg volume → exit signal).
     *
     * @param bars OHLCV bars sorted ASCENDING
     * @param days number of consecutive days to check
     * @param thresholdRatio volume threshold relative to average (e.g., 0.5 = 50%)
     * @param avgPeriod period for average volume calculation
     * @return true if volume has been below threshold for N consecutive days
     */
    public boolean isVolumeDeclining(List<OhlcvBar> bars, int days, BigDecimal thresholdRatio, int avgPeriod) {
        if (bars == null || bars.size() < avgPeriod + days) {
            return false;
        }

        BigDecimal avgVol = averageVolume(bars.subList(0, bars.size() - days), avgPeriod);
        if (avgVol == null || avgVol.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal threshold = avgVol.multiply(thresholdRatio);

        // Check last N bars
        for (int i = bars.size() - days; i < bars.size(); i++) {
            Long vol = bars.get(i).getVolume();
            if (vol == null || new BigDecimal(vol).compareTo(threshold) >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate the price range as a percentage over the last N days.
     * Used by momentum strategy to check if stock was consolidating (range < 15%).
     *
     * @param bars OHLCV bars sorted ASCENDING
     * @param period number of days
     * @return range as percentage, or null if insufficient data
     */
    public BigDecimal priceRangePct(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period) {
            return null;
        }

        BigDecimal high = BigDecimal.ZERO;
        BigDecimal low = null;

        int start = bars.size() - period;
        for (int i = start; i < bars.size(); i++) {
            BigDecimal h = bars.get(i).getHighPrice();
            BigDecimal l = bars.get(i).getLowPrice();
            if (h != null && h.compareTo(high) > 0) high = h;
            if (l != null && (low == null || l.compareTo(low) < 0)) low = l;
        }

        if (low == null || low.compareTo(BigDecimal.ZERO) == 0) return null;

        return high.subtract(low)
                .divide(low, SCALE, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}
