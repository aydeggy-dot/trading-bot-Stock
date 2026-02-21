package com.ngxbot.signal.technical;

import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.IndicatorSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Facade service that computes ALL technical indicators for a given symbol.
 * Pulls OHLCV data from the repository and delegates to individual calculators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalIndicatorService {

    private static final int DATA_LOOKBACK_DAYS = 100; // Enough for EMA(26) + MACD signal(9)

    private final OhlcvRepository ohlcvRepository;
    private final RsiCalculator rsiCalculator;
    private final MacdCalculator macdCalculator;
    private final MovingAverageCalculator movingAverageCalculator;
    private final VolumeAnalyzer volumeAnalyzer;
    private final AtrCalculator atrCalculator;

    /**
     * Compute all technical indicators for a symbol as of the given date.
     *
     * @param symbol stock symbol
     * @param asOfDate date to compute indicators for
     * @return IndicatorSnapshot with all computed values, or null if insufficient data
     */
    public IndicatorSnapshot computeSnapshot(String symbol, LocalDate asOfDate) {
        log.info("Computing indicator snapshot for {} as of {}", symbol, asOfDate);

        LocalDate from = asOfDate.minusDays(DATA_LOOKBACK_DAYS);
        List<OhlcvBar> bars = ohlcvRepository
                .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, asOfDate);

        if (bars.isEmpty()) {
            log.warn("No OHLCV data found for {} between {} and {}", symbol, from, asOfDate);
            return null;
        }

        OhlcvBar latestBar = bars.get(bars.size() - 1);

        // RSI(14)
        BigDecimal rsi14 = rsiCalculator.calculateRsi14(bars);

        // MACD(12,26,9)
        MacdCalculator.MacdResult macdResult = macdCalculator.calculate(bars);
        BigDecimal macdLine = macdResult != null ? macdResult.macdLine() : null;
        BigDecimal macdSignal = macdResult != null ? macdResult.signalLine() : null;
        BigDecimal macdHistogram = macdResult != null ? macdResult.histogram() : null;

        // Moving Averages
        BigDecimal sma20 = movingAverageCalculator.sma20(bars);
        BigDecimal ema12 = movingAverageCalculator.ema12(bars);
        BigDecimal ema26 = movingAverageCalculator.ema26(bars);

        // ATR(14)
        BigDecimal atr14 = atrCalculator.calculateAtr14(bars);

        // Volume
        BigDecimal volumeRatio = volumeAnalyzer.volumeRatio20d(bars);
        BigDecimal obv = volumeAnalyzer.obv(bars);
        BigDecimal avgVolume20d = volumeAnalyzer.averageVolume20d(bars);

        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                rsi14,
                macdLine,
                macdSignal,
                macdHistogram,
                sma20,
                ema12,
                ema26,
                atr14,
                volumeRatio,
                obv,
                latestBar.getClosePrice(),
                latestBar.getVolume(),
                avgVolume20d != null ? avgVolume20d.longValue() : null
        );

        log.info("Indicators for {}: RSI={}, MACD={}, SMA20={}, Vol ratio={}",
                symbol, rsi14, macdLine, sma20, volumeRatio);
        return snapshot;
    }

    /**
     * Compute snapshot using today's date.
     */
    public IndicatorSnapshot computeSnapshot(String symbol) {
        return computeSnapshot(symbol, LocalDate.now());
    }

    /**
     * Get raw OHLCV bars for a symbol (for use by strategies that need direct access).
     */
    public List<OhlcvBar> getBars(String symbol, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        return ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
    }
}
