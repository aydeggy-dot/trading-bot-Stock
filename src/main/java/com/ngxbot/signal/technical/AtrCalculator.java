package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Average True Range (ATR) calculator.
 * True Range = max(high - low, |high - prevClose|, |low - prevClose|)
 * ATR = Wilder's smoothed average of True Range over the period.
 */
@Slf4j
@Service
public class AtrCalculator {

    private static final int SCALE = 4;

    /**
     * Calculate ATR for a given period.
     *
     * @param bars OHLCV bars sorted by date ASCENDING (oldest first)
     * @param period ATR period (typically 14)
     * @return ATR value, or null if insufficient data
     */
    public BigDecimal calculate(List<OhlcvBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            log.debug("Insufficient data for ATR({}): need {} bars, have {}",
                    period, period + 1, bars == null ? 0 : bars.size());
            return null;
        }

        // Calculate True Range for each bar (starting from index 1)
        BigDecimal[] trueRanges = new BigDecimal[bars.size() - 1];
        for (int i = 1; i < bars.size(); i++) {
            trueRanges[i - 1] = trueRange(bars.get(i), bars.get(i - 1));
        }

        // First ATR: simple average of first 'period' true ranges
        BigDecimal atr = BigDecimal.ZERO;
        BigDecimal periodBd = new BigDecimal(period);

        for (int i = 0; i < period; i++) {
            atr = atr.add(trueRanges[i]);
        }
        atr = atr.divide(periodBd, SCALE, RoundingMode.HALF_UP);

        // Wilder's smoothing for remaining values
        BigDecimal periodMinusOne = new BigDecimal(period - 1);
        for (int i = period; i < trueRanges.length; i++) {
            atr = atr.multiply(periodMinusOne).add(trueRanges[i])
                    .divide(periodBd, SCALE, RoundingMode.HALF_UP);
        }

        log.debug("ATR({}) = {}", period, atr);
        return atr;
    }

    /**
     * Calculate ATR(14) — the standard period.
     */
    public BigDecimal calculateAtr14(List<OhlcvBar> bars) {
        return calculate(bars, 14);
    }

    /**
     * Calculate True Range for a single bar.
     * TR = max(high - low, |high - prevClose|, |low - prevClose|)
     */
    public BigDecimal trueRange(OhlcvBar current, OhlcvBar previous) {
        BigDecimal highLow = current.getHighPrice().subtract(current.getLowPrice());
        BigDecimal highPrevClose = current.getHighPrice().subtract(previous.getClosePrice()).abs();
        BigDecimal lowPrevClose = current.getLowPrice().subtract(previous.getClosePrice()).abs();

        return highLow.max(highPrevClose).max(lowPrevClose);
    }
}
