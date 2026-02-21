package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.model.IndicatorSnapshot;
import com.ngxbot.signal.technical.TechnicalIndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValueAccumulationStrategy implements Strategy {

    private final StrategyProperties strategyProperties;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final TradingProperties tradingProperties;

    @Override
    public String getName() {
        return "VALUE_ACCUMULATION";
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getValueAccumulation().isEnabled();
    }

    @Override
    public StrategyPool getPool() {
        return StrategyPool.CORE;
    }

    @Override
    public StrategyMarket getMarket() {
        return StrategyMarket.BOTH;
    }

    @Override
    public List<TradeSignal> evaluate(String symbol, LocalDate date) {
        if (!isEnabled()) return List.of();

        try {
            IndicatorSnapshot rawSnap = technicalIndicatorService.computeSnapshot(symbol);
            Optional<IndicatorSnapshot> optSnap = Optional.ofNullable(rawSnap);
            if (optSnap.isEmpty()) return List.of();

            IndicatorSnapshot snap = optSnap.get();
            if (snap.currentPrice() == null || snap.currentPrice().compareTo(BigDecimal.ZERO) <= 0) return List.of();

            int score = 0;

            // RSI below 40 = undervalued signal
            if (snap.rsi14() != null && snap.rsi14().compareTo(new BigDecimal("40")) < 0) {
                score += 20;
            }

            // Price below SMA20 = discount
            if (snap.sma20() != null && snap.currentPrice().compareTo(snap.sma20()) < 0) {
                score += 15;
            }

            // MACD histogram turning positive
            if (snap.macdHistogram() != null && snap.macdHistogram().compareTo(BigDecimal.ZERO) > 0) {
                score += 15;
            }

            // Volume ratio > 0.8 = not dying
            if (snap.volumeRatio() != null && snap.volumeRatio().compareTo(new BigDecimal("0.8")) > 0) {
                score += 10;
            }

            // RSI between 30-50 = additional value zone
            if (snap.rsi14() != null && snap.rsi14().compareTo(new BigDecimal("30")) >= 0
                    && snap.rsi14().compareTo(new BigDecimal("50")) <= 0) {
                score += 10;
            }

            // EMA12 > EMA26 = trend turning up
            if (snap.ema12() != null && snap.ema26() != null
                    && snap.ema12().compareTo(snap.ema26()) > 0) {
                score += 10;
            }

            // Volume increasing
            if (snap.volumeRatio() != null && snap.volumeRatio().compareTo(new BigDecimal("1.2")) > 0) {
                score += 10;
            }

            // Positive MACD crossover (line above signal)
            if (snap.macdLine() != null && snap.macdSignal() != null
                    && snap.macdLine().compareTo(snap.macdSignal()) > 0) {
                score += 10;
            }

            score = Math.min(score, 100);

            int minScore = strategyProperties.getValueAccumulation().getMinFundamentalScore();
            if (score < minScore) {
                log.debug("{}: value score {} below threshold {}", symbol, score, minScore);
                return List.of();
            }

            BigDecimal currentPrice = snap.currentPrice();
            BigDecimal target = snap.sma20() != null ? snap.sma20()
                    : currentPrice.multiply(new BigDecimal("1.10")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal stopLoss = currentPrice.multiply(new BigDecimal("0.88")).setScale(4, RoundingMode.HALF_UP);

            String reasoning = String.format("Value score %d/%d: RSI=%.1f, price %s SMA20, MACD hist=%.4f",
                    score, 100,
                    snap.rsi14() != null ? snap.rsi14() : BigDecimal.ZERO,
                    snap.sma20() != null && currentPrice.compareTo(snap.sma20()) < 0 ? "below" : "above",
                    snap.macdHistogram() != null ? snap.macdHistogram() : BigDecimal.ZERO);

            SignalStrength strength = score >= 80 ? SignalStrength.STRONG_BUY : SignalStrength.BUY;

            return List.of(new TradeSignal(
                    symbol, TradeSide.BUY, currentPrice, stopLoss, target,
                    strength, score, getName(), reasoning, snap, date
            ));
        } catch (Exception e) {
            log.error("{}: value evaluation failed: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> getTargetSymbols() {
        List<String> symbols = new ArrayList<>();
        symbols.addAll(tradingProperties.getWatchlist().getEtfs());
        symbols.addAll(tradingProperties.getWatchlist().getLargeCaps());
        return symbols;
    }
}
