package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.EtfValuation;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.signal.fundamental.NavDiscountCalculator;
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
import java.util.Optional;

/**
 * ETF NAV Arbitrage Strategy.
 *
 * ENTRY (ALL must be true):
 *   1. ETF market_price < (nav * 0.90) → 10%+ discount to NAV
 *   2. RSI(14) < 60
 *   3. today_volume > (avg_volume_20d * 1.2)
 *   4. OPTIONAL STRONG BUY: yesterday's discount was LARGER than today's (gap narrowing)
 *
 * EXIT (ANY triggers):
 *   1. market_price > (nav * 1.20) → Sell 50%
 *   2. market_price > (nav * 1.40) → Sell remaining
 *   3. RSI(14) > 70 AND discount <= 0% → Mean reversion complete
 *   4. Volume < (avg_volume_20d * 0.5) for 3 consecutive days
 *   5. Stop-loss: entry_price * 0.92
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtfNavArbitrageStrategy implements Strategy {

    private static final String STRATEGY_NAME = "ETF_NAV_ARBITRAGE";

    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final NavDiscountCalculator navDiscountCalculator;
    private final VolumeAnalyzer volumeAnalyzer;

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getEtfNavArbitrage().isEnabled();
    }

    @Override
    public List<String> getTargetSymbols() {
        return tradingProperties.getWatchlist().getEtfs();
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        if (!isEnabled()) return List.of();

        List<TradeSignal> signals = new ArrayList<>();
        StrategyProperties.EtfNavArbitrage config = strategyProperties.getEtfNavArbitrage();

        // Get NAV data
        Optional<EtfValuation> valuationOpt = navDiscountCalculator.getLatestValuation(symbol);
        if (valuationOpt.isEmpty()) {
            log.debug("No NAV data for {} — skipping", symbol);
            return signals;
        }

        EtfValuation valuation = valuationOpt.get();
        BigDecimal premiumDiscountPct = valuation.getPremiumDiscountPct();
        if (premiumDiscountPct == null) {
            return signals;
        }

        // Get technical indicators
        IndicatorSnapshot indicators = technicalIndicatorService.computeSnapshot(symbol, date);
        if (indicators == null) {
            log.debug("No indicator data for {} — skipping", symbol);
            return signals;
        }

        // Check for ENTRY signals
        TradeSignal entrySignal = evaluateEntry(symbol, date, valuation, indicators, config);
        if (entrySignal != null) {
            signals.add(entrySignal);
        }

        // Check for EXIT signals
        TradeSignal exitSignal = evaluateExit(symbol, date, valuation, indicators, config);
        if (exitSignal != null) {
            signals.add(exitSignal);
        }

        return signals;
    }

    private TradeSignal evaluateEntry(String symbol, LocalDate date,
                                      EtfValuation valuation,
                                      IndicatorSnapshot indicators,
                                      StrategyProperties.EtfNavArbitrage config) {
        BigDecimal discount = valuation.getPremiumDiscountPct();
        BigDecimal entryThreshold = config.getEntryDiscountPct().negate(); // -10.0

        // Condition 1: discount >= entry threshold (e.g., discount <= -10%)
        if (discount.compareTo(entryThreshold) > 0) {
            log.debug("{}: discount {}% above entry threshold {}%", symbol, discount, entryThreshold);
            return null;
        }

        // Condition 2: RSI(14) < maxRsi
        if (indicators.rsi14() == null || indicators.rsi14().intValue() >= config.getMaxRsi()) {
            log.debug("{}: RSI {} >= max {}", symbol, indicators.rsi14(), config.getMaxRsi());
            return null;
        }

        // Condition 3: volume ratio > min
        if (indicators.volumeRatio() == null ||
                indicators.volumeRatio().compareTo(config.getMinVolumeRatio()) < 0) {
            log.debug("{}: volume ratio {} < min {}", symbol, indicators.volumeRatio(), config.getMinVolumeRatio());
            return null;
        }

        // All entry conditions met
        boolean isNarrowing = navDiscountCalculator.isDiscountNarrowing(symbol);
        SignalStrength strength = isNarrowing ? SignalStrength.STRONG_BUY : SignalStrength.BUY;

        // Confidence based on discount depth
        int confidence = calculateEntryConfidence(discount, indicators, isNarrowing);

        // Stop-loss: entry_price * 0.92
        BigDecimal stopLoss = indicators.currentPrice()
                .multiply(new BigDecimal("0.92"))
                .setScale(4, RoundingMode.HALF_UP);

        // Target: NAV (mean reversion target)
        BigDecimal target = valuation.getNav();

        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("ETF trading at %.1f%% discount to NAV. ", discount));
        reasoning.append(String.format("RSI=%.1f, Vol ratio=%.1f. ", indicators.rsi14(), indicators.volumeRatio()));
        if (isNarrowing) {
            reasoning.append("Discount narrowing (bullish). ");
        }
        if (discount.compareTo(new BigDecimal("-25")) <= 0) {
            reasoning.append("DEEP DISCOUNT (>25%) — HIGH PRIORITY. ");
        }

        return new TradeSignal(
                symbol,
                TradeSide.BUY,
                indicators.currentPrice(),
                stopLoss,
                target,
                strength,
                confidence,
                STRATEGY_NAME,
                reasoning.toString(),
                indicators,
                date
        );
    }

    private TradeSignal evaluateExit(String symbol, LocalDate date,
                                     EtfValuation valuation,
                                     IndicatorSnapshot indicators,
                                     StrategyProperties.EtfNavArbitrage config) {
        BigDecimal discount = valuation.getPremiumDiscountPct();
        if (discount == null) return null;

        StringBuilder reasoning = new StringBuilder();
        boolean shouldExit = false;
        SignalStrength strength = SignalStrength.SELL;

        // Exit 1: market_price > (nav * 1.20) → premium >= 20%
        if (discount.compareTo(config.getExitPremiumPct()) >= 0) {
            reasoning.append(String.format("Premium %.1f%% >= exit threshold %.1f%%. ",
                    discount, config.getExitPremiumPct()));
            shouldExit = true;
        }

        // Exit 2: market_price > (nav * 1.40) → extreme premium >= 50%
        if (discount.compareTo(config.getExtremePremiumPct()) >= 0) {
            reasoning.append("EXTREME PREMIUM — sell all remaining. ");
            strength = SignalStrength.STRONG_SELL;
            shouldExit = true;
        }

        // Exit 3: RSI > 70 AND discount <= 0% (mean reversion complete)
        if (indicators.rsi14() != null && indicators.rsi14().intValue() > 70
                && discount.compareTo(BigDecimal.ZERO) <= 0) {
            reasoning.append(String.format("RSI=%.1f > 70 and no discount — mean reversion complete. ",
                    indicators.rsi14()));
            shouldExit = true;
        }

        // Exit 4: Volume declining for 3 consecutive days
        List<OhlcvBar> bars = technicalIndicatorService.getBars(symbol, 30);
        if (volumeAnalyzer.isVolumeDeclining(bars, 3, new BigDecimal("0.5"), 20)) {
            reasoning.append("Volume declining for 3 consecutive days. ");
            shouldExit = true;
        }

        if (!shouldExit) return null;

        return new TradeSignal(
                symbol,
                TradeSide.SELL,
                indicators.currentPrice(),
                null, // no stop-loss on exit signals
                null, // no target on exit signals
                strength,
                75,
                STRATEGY_NAME,
                reasoning.toString(),
                indicators,
                date
        );
    }

    private int calculateEntryConfidence(BigDecimal discount, IndicatorSnapshot indicators, boolean isNarrowing) {
        int confidence = 50; // Base

        // Deeper discount = higher confidence
        if (discount.compareTo(new BigDecimal("-25")) <= 0) {
            confidence += 25;
        } else if (discount.compareTo(new BigDecimal("-15")) <= 0) {
            confidence += 15;
        } else {
            confidence += 5;
        }

        // Discount narrowing = higher confidence
        if (isNarrowing) confidence += 10;

        // Low RSI = better entry
        if (indicators.rsi14() != null && indicators.rsi14().intValue() < 40) {
            confidence += 10;
        }

        // High volume ratio = more conviction
        if (indicators.volumeRatio() != null && indicators.volumeRatio().compareTo(new BigDecimal("2.0")) > 0) {
            confidence += 5;
        }

        return Math.min(confidence, 100);
    }
}
