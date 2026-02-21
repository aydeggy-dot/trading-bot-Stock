package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
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
 * US Earnings Momentum Strategy.
 *
 * Targets US-listed equities around earnings announcements. Generates BUY signals
 * when a stock shows positive post-earnings momentum with reasonable RSI and
 * sufficient volume.
 *
 * ENTRY (ALL must be true):
 *   1. RSI(14) < 70 — not overbought
 *   2. avgVolume20d > 100,000 — sufficient liquidity
 *   3. ATR available for stop/target calculation
 *
 * POSITION SIZING:
 *   - Stop: entry - 1.5 * ATR(14)
 *   - Target: min(entry * 1.10, entry + 2 * ATR(14))
 *   - Confidence: base 70, adjusted by earnings surprise magnitude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsEarningsMomentumStrategy implements Strategy {

    private static final String STRATEGY_NAME = "US_EARNINGS_MOMENTUM";

    private final StrategyProperties strategyProperties;
    private final OhlcvRepository ohlcvRepository;
    private final TechnicalIndicatorService technicalIndicatorService;

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getUsEarningsMomentum().isEnabled();
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
        // Populated dynamically from earnings calendar — not a static watchlist
        return List.of();
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        if (!isEnabled()) {
            return List.of();
        }

        StrategyProperties.UsEarningsMomentum config = strategyProperties.getUsEarningsMomentum();

        IndicatorSnapshot indicators = technicalIndicatorService.computeSnapshot(symbol, date);
        if (indicators == null) {
            log.debug("{}: No indicator data available — skipping", symbol);
            return List.of();
        }

        // Condition 1: RSI must be below threshold (not overbought)
        if (indicators.rsi14() == null || indicators.rsi14().intValue() >= config.getMaxRsi()) {
            log.debug("{}: RSI {} >= max {} — skipping", symbol, indicators.rsi14(), config.getMaxRsi());
            return List.of();
        }

        // Condition 2: Average daily volume must exceed minimum
        if (indicators.avgVolume20d() == null || indicators.avgVolume20d() < config.getMinAvgVolume()) {
            log.debug("{}: avgVolume20d {} < min {} — skipping",
                    symbol, indicators.avgVolume20d(), config.getMinAvgVolume());
            return List.of();
        }

        // Condition 3: ATR must be available for stop/target calculation
        if (indicators.atr14() == null || indicators.currentPrice() == null) {
            log.debug("{}: ATR or currentPrice unavailable — skipping", symbol);
            return List.of();
        }

        // All conditions met — generate BUY signal
        BigDecimal entryPrice = indicators.currentPrice();
        BigDecimal atr = indicators.atr14();

        // Stop-loss: entry - 1.5 * ATR
        BigDecimal stopLoss = entryPrice
                .subtract(atr.multiply(config.getAtrStopMultiplier()))
                .setScale(4, RoundingMode.HALF_UP);

        // Target: min(entry * 1.10, entry + 2 * ATR)
        BigDecimal pctTarget = entryPrice
                .multiply(BigDecimal.ONE.add(config.getMaxTargetPct()))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal atrTarget = entryPrice
                .add(atr.multiply(config.getAtrTargetMultiplier()))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal target = pctTarget.min(atrTarget);

        // Confidence: base score adjusted by earnings surprise proximity
        int confidence = calculateConfidence(indicators, config);

        SignalStrength strength = confidence >= 80 ? SignalStrength.STRONG_BUY : SignalStrength.BUY;

        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("US earnings momentum: RSI=%.1f (< %d), ",
                indicators.rsi14(), config.getMaxRsi()));
        reasoning.append(String.format("avgVol=%d (> %d), ",
                indicators.avgVolume20d(), config.getMinAvgVolume()));
        reasoning.append(String.format("entry=%.2f, stop=%.2f (ATR*%.1f), target=%.2f. ",
                entryPrice, stopLoss, config.getAtrStopMultiplier(), target));
        if (indicators.volumeRatio() != null && indicators.volumeRatio().compareTo(new BigDecimal("1.5")) > 0) {
            reasoning.append(String.format("Elevated volume ratio %.1fx suggests institutional interest. ",
                    indicators.volumeRatio()));
        }

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

        log.info("{}: Generated BUY signal — confidence={}, stop={}, target={}",
                symbol, confidence, stopLoss, target);

        return List.of(signal);
    }

    /**
     * Calculate confidence score based on technical indicators.
     * Starts from a base value and adjusts for favorable conditions.
     */
    private int calculateConfidence(IndicatorSnapshot indicators,
                                    StrategyProperties.UsEarningsMomentum config) {
        int confidence = config.getBaseConfidence();

        // RSI well below threshold = stronger entry
        if (indicators.rsi14() != null && indicators.rsi14().intValue() < 50) {
            confidence += 10;
        }

        // High volume ratio suggests earnings surprise / institutional flow
        if (indicators.volumeRatio() != null) {
            if (indicators.volumeRatio().compareTo(new BigDecimal("2.0")) > 0) {
                confidence += 15;
            } else if (indicators.volumeRatio().compareTo(new BigDecimal("1.5")) > 0) {
                confidence += 10;
            }
        }

        // Positive MACD histogram = momentum confirmation
        if (indicators.macdHistogram() != null
                && indicators.macdHistogram().compareTo(BigDecimal.ZERO) > 0) {
            confidence += 5;
        }

        return Math.min(confidence, 100);
    }
}
