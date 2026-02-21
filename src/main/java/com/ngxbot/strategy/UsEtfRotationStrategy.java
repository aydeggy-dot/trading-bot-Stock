package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.IndicatorSnapshot;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.technical.TechnicalIndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * US ETF Sector Rotation Strategy.
 *
 * Evaluates SPDR sector ETFs monthly and rotates into the strongest-scoring sectors.
 * Only generates signals on the first trading day of each month (day of month <= 3).
 *
 * SCORING (0-100):
 *   - 1-month return (20 bars): 40% weight
 *   - 3-month return (60 bars): 30% weight
 *   - RSI sweet spot (50-70):   15% weight
 *   - Volume trend (ratio > 1): 15% weight
 *
 * ENTRY:
 *   - Score > 60 → BUY
 *   - Target: entry * 1.05
 *   - Stop: entry * 0.95
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsEtfRotationStrategy implements Strategy {

    private static final String STRATEGY_NAME = "US_ETF_ROTATION";

    private static final List<String> SECTOR_ETFS = List.of(
            "XLF", "XLK", "XLE", "XLV", "XLI", "XLC", "XLY", "XLP", "XLU", "XLRE"
    );

    private final StrategyProperties strategyProperties;
    private final OhlcvRepository ohlcvRepository;
    private final TechnicalIndicatorService technicalIndicatorService;

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getUsEtfRotation().isEnabled();
    }

    @Override
    public StrategyPool getPool() {
        return StrategyPool.SATELLITE;
    }

    @Override
    public StrategyMarket getMarket() {
        return StrategyMarket.US;
    }

    @Override
    public List<String> getTargetSymbols() {
        return SECTOR_ETFS;
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        if (!isEnabled()) {
            return List.of();
        }

        StrategyProperties.UsEtfRotation config = strategyProperties.getUsEtfRotation();

        // Only generate signals on the first trading day of the month (day <= 3)
        if (date.getDayOfMonth() > config.getMaxDayOfMonth()) {
            log.debug("{}: Day {} > {} — not a rotation day, skipping",
                    symbol, date.getDayOfMonth(), config.getMaxDayOfMonth());
            return List.of();
        }

        IndicatorSnapshot indicators = technicalIndicatorService.computeSnapshot(symbol, date);
        if (indicators == null) {
            log.debug("{}: No indicator data available — skipping", symbol);
            return List.of();
        }

        if (indicators.currentPrice() == null) {
            log.debug("{}: No current price — skipping", symbol);
            return List.of();
        }

        // Calculate composite sector score
        BigDecimal score = calculateSectorScore(symbol, date, indicators, config);
        log.debug("{}: Sector rotation score = {}", symbol, score);

        // Only generate signal if score exceeds threshold
        if (score.compareTo(config.getMinScore()) < 0) {
            log.debug("{}: Score {} < min {} — no signal", symbol, score, config.getMinScore());
            return List.of();
        }

        BigDecimal entryPrice = indicators.currentPrice();

        // Target: entry * 1.05
        BigDecimal target = entryPrice
                .multiply(BigDecimal.ONE.add(config.getTargetPct()))
                .setScale(4, RoundingMode.HALF_UP);

        // Stop: entry * 0.95
        BigDecimal stopLoss = entryPrice
                .multiply(BigDecimal.ONE.subtract(config.getStopPct()))
                .setScale(4, RoundingMode.HALF_UP);

        int confidence = score.intValue();
        SignalStrength strength = confidence >= 80 ? SignalStrength.STRONG_BUY : SignalStrength.BUY;

        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("ETF sector rotation: %s scored %.1f/100. ", symbol, score));
        if (indicators.rsi14() != null) {
            reasoning.append(String.format("RSI=%.1f. ", indicators.rsi14()));
        }
        if (indicators.volumeRatio() != null) {
            reasoning.append(String.format("Volume ratio=%.1fx. ", indicators.volumeRatio()));
        }
        reasoning.append(String.format("Monthly rebalance on %s. ", date));

        TradeSignal signal = new TradeSignal(
                symbol,
                TradeSide.BUY,
                entryPrice,
                stopLoss,
                target,
                strength,
                confidence,
                STRATEGY_NAME,
                reasoning.toString(),
                indicators,
                date
        );

        log.info("{}: Generated BUY signal — score={}, stop={}, target={}",
                symbol, confidence, stopLoss, target);

        return List.of(signal);
    }

    /**
     * Calculate a composite sector score from four components:
     * <ol>
     *   <li>1-month return (20 bars): 40% weight</li>
     *   <li>3-month return (60 bars): 30% weight</li>
     *   <li>RSI sweet spot (50-70): 15% weight</li>
     *   <li>Volume trend (volumeRatio > 1.0): 15% weight</li>
     * </ol>
     *
     * @return score normalized to 0-100
     */
    private BigDecimal calculateSectorScore(String symbol, LocalDate date,
                                            IndicatorSnapshot indicators,
                                            StrategyProperties.UsEtfRotation config) {
        BigDecimal score = BigDecimal.ZERO;

        // Component 1: 1-month return (last 20 bars price change), weight = 40%
        BigDecimal oneMonthReturn = calculateReturn(symbol, date, 20);
        if (oneMonthReturn != null) {
            // Normalize: assume a 10% monthly return = 100 score for this component
            BigDecimal normalized = oneMonthReturn
                    .multiply(new BigDecimal("1000"))
                    .max(BigDecimal.ZERO)
                    .min(new BigDecimal("100"));
            score = score.add(normalized.multiply(config.getOneMonthWeight()));
        }

        // Component 2: 3-month return (last 60 bars price change), weight = 30%
        BigDecimal threeMonthReturn = calculateReturn(symbol, date, 60);
        if (threeMonthReturn != null) {
            // Normalize: assume a 20% quarterly return = 100 score for this component
            BigDecimal normalized = threeMonthReturn
                    .multiply(new BigDecimal("500"))
                    .max(BigDecimal.ZERO)
                    .min(new BigDecimal("100"));
            score = score.add(normalized.multiply(config.getThreeMonthWeight()));
        }

        // Component 3: RSI sweet spot (50-70), weight = 15%
        if (indicators.rsi14() != null) {
            BigDecimal rsi = indicators.rsi14();
            BigDecimal rsiScore;
            int rsiValue = rsi.intValue();
            if (rsiValue >= 50 && rsiValue <= 70) {
                // In the sweet spot: full score
                rsiScore = new BigDecimal("100");
            } else if (rsiValue >= 40 && rsiValue < 50) {
                // Approaching sweet spot from below
                rsiScore = new BigDecimal("60");
            } else if (rsiValue > 70 && rsiValue <= 80) {
                // Slightly above sweet spot
                rsiScore = new BigDecimal("40");
            } else {
                // Outside useful range
                rsiScore = BigDecimal.ZERO;
            }
            score = score.add(rsiScore.multiply(config.getRsiWeight()));
        }

        // Component 4: Volume trend (volumeRatio > 1.0 = positive), weight = 15%
        if (indicators.volumeRatio() != null) {
            BigDecimal volScore;
            if (indicators.volumeRatio().compareTo(new BigDecimal("1.5")) >= 0) {
                volScore = new BigDecimal("100");
            } else if (indicators.volumeRatio().compareTo(BigDecimal.ONE) >= 0) {
                // Linear scale from 1.0 (50) to 1.5 (100)
                volScore = indicators.volumeRatio()
                        .subtract(BigDecimal.ONE)
                        .multiply(new BigDecimal("100"))
                        .add(new BigDecimal("50"))
                        .min(new BigDecimal("100"));
            } else {
                // Below average volume
                volScore = indicators.volumeRatio()
                        .multiply(new BigDecimal("50"))
                        .max(BigDecimal.ZERO);
            }
            score = score.add(volScore.multiply(config.getVolumeWeight()));
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the price return over the specified number of trading bars.
     *
     * @param symbol the stock/ETF symbol
     * @param date   the evaluation date
     * @param bars   number of bars to look back
     * @return fractional return (e.g. 0.05 = 5%), or null if insufficient data
     */
    private BigDecimal calculateReturn(String symbol, LocalDate date, int bars) {
        // Fetch enough bars with some buffer for non-trading days
        int lookbackDays = (int) (bars * 1.5) + 10;
        LocalDate from = date.minusDays(lookbackDays);

        List<OhlcvBar> ohlcvBars = ohlcvRepository
                .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, date);

        if (ohlcvBars.size() < bars) {
            log.debug("{}: Insufficient data for {}-bar return (have {})",
                    symbol, bars, ohlcvBars.size());
            return null;
        }

        OhlcvBar latest = ohlcvBars.get(ohlcvBars.size() - 1);
        OhlcvBar earlier = ohlcvBars.get(ohlcvBars.size() - bars);

        if (latest.getClosePrice() == null || earlier.getClosePrice() == null
                || earlier.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return latest.getClosePrice()
                .subtract(earlier.getClosePrice())
                .divide(earlier.getClosePrice(), 6, RoundingMode.HALF_UP);
    }
}
