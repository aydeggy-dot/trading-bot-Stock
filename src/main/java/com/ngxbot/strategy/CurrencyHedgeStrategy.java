package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyHedgeStrategy implements Strategy {

    private static final List<String> GOLD_SYMBOLS = List.of("NEWGOLD");

    private final StrategyProperties strategyProperties;
    private final OhlcvRepository ohlcvRepository;
    private final TechnicalIndicatorService technicalIndicatorService;

    @Override
    public String getName() {
        return "CURRENCY_HEDGE";
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getCurrencyHedge().isEnabled();
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

            // Check 30-day price trend as proxy for naira weakness
            List<OhlcvBar> bars = ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                    symbol, date.minusDays(35), date);

            if (bars.size() < 20) {
                log.debug("{}: insufficient bars for 30-day trend ({})", symbol, bars.size());
                return List.of();
            }

            BigDecimal priceNow = snap.currentPrice();
            BigDecimal price30dAgo = bars.get(0).getClosePrice();

            if (price30dAgo.compareTo(BigDecimal.ZERO) <= 0) return List.of();

            BigDecimal returnPct = priceNow.subtract(price30dAgo)
                    .divide(price30dAgo, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            BigDecimal threshold = strategyProperties.getCurrencyHedge().getNairaWeaknessThreshold30dPct();

            if (returnPct.compareTo(threshold) > 0) {
                // Naira weakening detected — increase gold allocation
                BigDecimal stopLoss = priceNow.multiply(new BigDecimal("0.95")).setScale(4, RoundingMode.HALF_UP);
                BigDecimal target = priceNow.multiply(new BigDecimal("1.10")).setScale(4, RoundingMode.HALF_UP);

                String reasoning = String.format("Naira weakness proxy: %s up %.2f%% in 30 days (threshold: %.1f%%)",
                        symbol, returnPct, threshold);

                return List.of(new TradeSignal(
                        symbol, TradeSide.BUY, priceNow, stopLoss, target,
                        SignalStrength.BUY, 70, getName(), reasoning, snap, date
                ));
            }

            log.debug("{}: 30-day return {}% below weakness threshold {}%", symbol, returnPct, threshold);
            return List.of();
        } catch (Exception e) {
            log.error("{}: currency hedge evaluation failed: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> getTargetSymbols() {
        return GOLD_SYMBOLS;
    }
}
