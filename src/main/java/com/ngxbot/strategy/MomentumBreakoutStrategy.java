package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.signal.model.IndicatorSnapshot;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.technical.TechnicalIndicatorService;
import com.ngxbot.signal.technical.VolumeAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Momentum Breakout Strategy.
 *
 * ENTRY (ALL must be true):
 *   1. today_volume > (avg_volume_20d * 3.0) — volume spike
 *   2. close_price > SMA(20) — above trend
 *   3. RSI(14) between 40 and 65 — not overbought
 *   4. Price range over last 20 days < 15% — was consolidating
 *   5. avg_daily_volume > 10,000 shares — liquidity minimum
 *   6. BONUS: if stock hit +10% daily limit → flag "likely continuation"
 *
 * EXIT (scale-out):
 *   1. Sell 50% at entry_price + (ATR(14) * 2)
 *   2. Trail stop: entry_price + (ATR(14) * 1) trailing
 *   3. RSI > 75 → sell all remaining
 *   4. Volume < (avg_volume_20d * 0.5) for 3 consecutive days → sell all
 *   5. Stop-loss: entry_price - (ATR(14) * 2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentumBreakoutStrategy implements Strategy {

    private static final String STRATEGY_NAME = "MOMENTUM_BREAKOUT";
    private static final BigDecimal MIN_AVG_VOLUME = new BigDecimal("10000");
    private static final BigDecimal CONSOLIDATION_RANGE_PCT = new BigDecimal("15.0");

    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final VolumeAnalyzer volumeAnalyzer;

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getMomentumBreakout().isEnabled();
    }

    @Override
    public List<String> getTargetSymbols() {
        List<String> symbols = new ArrayList<>();
        symbols.addAll(tradingProperties.getWatchlist().getLargeCaps());
        symbols.addAll(tradingProperties.getWatchlist().getEtfs());
        return symbols;
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        if (!isEnabled()) return List.of();

        List<TradeSignal> signals = new ArrayList<>();
        StrategyProperties.MomentumBreakout config = strategyProperties.getMomentumBreakout();

        IndicatorSnapshot indicators = technicalIndicatorService.computeSnapshot(symbol, date);
        if (indicators == null) {
            return signals;
        }

        // Check entry conditions
        TradeSignal entrySignal = evaluateEntry(symbol, date, indicators, config);
        if (entrySignal != null) {
            signals.add(entrySignal);
        }

        // Check exit conditions
        TradeSignal exitSignal = evaluateExit(symbol, date, indicators, config);
        if (exitSignal != null) {
            signals.add(exitSignal);
        }

        return signals;
    }

    private TradeSignal evaluateEntry(String symbol, LocalDate date,
                                      IndicatorSnapshot indicators,
                                      StrategyProperties.MomentumBreakout config) {

        // Condition 1: Volume spike (today > avg * 3.0)
        if (indicators.volumeRatio() == null ||
                indicators.volumeRatio().compareTo(config.getVolumeSpikeRatio()) < 0) {
            return null;
        }

        // Condition 2: Price above SMA(20)
        if (indicators.sma20() == null || indicators.currentPrice() == null ||
                indicators.currentPrice().compareTo(indicators.sma20()) <= 0) {
            return null;
        }

        // Condition 3: RSI between minRsi and maxRsi
        if (indicators.rsi14() == null) return null;
        int rsi = indicators.rsi14().intValue();
        if (rsi < config.getMinRsi() || rsi > config.getMaxRsi()) {
            return null;
        }

        // Condition 4: Was consolidating (price range < 15%)
        List<OhlcvBar> bars = technicalIndicatorService.getBars(symbol, 30);
        BigDecimal rangePct = volumeAnalyzer.priceRangePct(bars, config.getSmaPeriod());
        if (rangePct == null || rangePct.compareTo(CONSOLIDATION_RANGE_PCT) >= 0) {
            return null;
        }

        // Condition 5: Liquidity minimum (avg volume > 10,000)
        if (indicators.avgVolume20d() == null || indicators.avgVolume20d() < MIN_AVG_VOLUME.longValue()) {
            return null;
        }

        // All entry conditions met!
        // Bonus: check if hit daily limit (+10%)
        boolean hitDailyLimit = checkDailyLimitHit(bars);

        SignalStrength strength = hitDailyLimit ? SignalStrength.STRONG_BUY : SignalStrength.BUY;
        int confidence = calculateConfidence(indicators, rangePct, hitDailyLimit);

        // Stop-loss: entry - ATR(14) * 2
        BigDecimal stopLoss = null;
        BigDecimal target = null;
        if (indicators.atr14() != null) {
            stopLoss = indicators.currentPrice()
                    .subtract(indicators.atr14().multiply(config.getAtrStopMultiplier()))
                    .setScale(4, RoundingMode.HALF_UP);
            target = indicators.currentPrice()
                    .add(indicators.atr14().multiply(config.getAtrTargetMultiplier()))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("Momentum breakout: vol spike %.1fx, ", indicators.volumeRatio()));
        reasoning.append(String.format("price above SMA20 (%.2f > %.2f), ", indicators.currentPrice(), indicators.sma20()));
        reasoning.append(String.format("RSI=%.1f, range=%.1f%%. ", indicators.rsi14(), rangePct));
        if (hitDailyLimit) {
            reasoning.append("HIT DAILY LIMIT (+10%) — likely continuation. ");
        }

        return new TradeSignal(
                symbol, TradeSide.BUY, indicators.currentPrice(),
                stopLoss, target, strength, confidence,
                STRATEGY_NAME, reasoning.toString(), indicators, date
        );
    }

    private TradeSignal evaluateExit(String symbol, LocalDate date,
                                     IndicatorSnapshot indicators,
                                     StrategyProperties.MomentumBreakout config) {
        StringBuilder reasoning = new StringBuilder();
        boolean shouldExit = false;
        SignalStrength strength = SignalStrength.SELL;

        // Exit 3: RSI > 75
        if (indicators.rsi14() != null && indicators.rsi14().intValue() > 75) {
            reasoning.append(String.format("RSI=%.1f > 75 — overbought, sell all. ", indicators.rsi14()));
            strength = SignalStrength.STRONG_SELL;
            shouldExit = true;
        }

        // Exit 4: Volume declining for 3 consecutive days
        List<OhlcvBar> bars = technicalIndicatorService.getBars(symbol, 30);
        if (volumeAnalyzer.isVolumeDeclining(bars, 3, new BigDecimal("0.5"), 20)) {
            reasoning.append("Volume declining 3 consecutive days below 50% avg. ");
            shouldExit = true;
        }

        if (!shouldExit) return null;

        return new TradeSignal(
                symbol, TradeSide.SELL, indicators.currentPrice(),
                null, null, strength, 70,
                STRATEGY_NAME, reasoning.toString(), indicators, date
        );
    }

    private boolean checkDailyLimitHit(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) return false;
        OhlcvBar latest = bars.get(bars.size() - 1);
        OhlcvBar previous = bars.get(bars.size() - 2);

        if (latest.getClosePrice() == null || previous.getClosePrice() == null ||
                previous.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal changePct = latest.getClosePrice().subtract(previous.getClosePrice())
                .divide(previous.getClosePrice(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        return changePct.compareTo(tradingProperties.getDailyPriceLimitPct()) >= 0;
    }

    private int calculateConfidence(IndicatorSnapshot indicators, BigDecimal rangePct, boolean hitLimit) {
        int confidence = 50;

        // Higher volume spike = more confidence
        if (indicators.volumeRatio() != null && indicators.volumeRatio().compareTo(new BigDecimal("5.0")) > 0) {
            confidence += 15;
        } else if (indicators.volumeRatio() != null && indicators.volumeRatio().compareTo(new BigDecimal("4.0")) > 0) {
            confidence += 10;
        }

        // Tighter consolidation = cleaner breakout
        if (rangePct != null && rangePct.compareTo(new BigDecimal("8.0")) < 0) {
            confidence += 10;
        }

        // MACD histogram positive = momentum confirmation
        if (indicators.macdHistogram() != null && indicators.macdHistogram().compareTo(BigDecimal.ZERO) > 0) {
            confidence += 5;
        }

        // Daily limit hit = institutional interest
        if (hitLimit) confidence += 15;

        return Math.min(confidence, 100);
    }
}
