package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
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
import java.time.Month;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectorRotationStrategy implements Strategy {

    private static final Map<String, List<String>> NGX_SECTORS = Map.of(
            "Banking", List.of("ZENITHBANK", "GTCO", "UBA", "ACCESSCORP", "FBNH"),
            "Industrial", List.of("DANGCEM", "BUACEMENT"),
            "Oil & Gas", List.of("SEPLAT", "ARADEL")
    );

    private static final Set<Month> ROTATION_MONTHS = Set.of(
            Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER
    );

    private final StrategyProperties strategyProperties;
    private final TechnicalIndicatorService technicalIndicatorService;

    @Override
    public String getName() {
        return "SECTOR_ROTATION";
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getSectorRotation().isEnabled();
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

        // Only generate signals on quarterly rotation months, first 5 days
        if (!ROTATION_MONTHS.contains(date.getMonth()) || date.getDayOfMonth() > 5) {
            return List.of();
        }

        try {
            IndicatorSnapshot rawSnap = technicalIndicatorService.computeSnapshot(symbol);
            Optional<IndicatorSnapshot> optSnap = Optional.ofNullable(rawSnap);
            if (optSnap.isEmpty()) return List.of();

            IndicatorSnapshot snap = optSnap.get();
            if (snap.currentPrice() == null || snap.currentPrice().compareTo(BigDecimal.ZERO) <= 0) return List.of();

            int score = calculateSectorScore(snap);

            if (score > 60) {
                BigDecimal currentPrice = snap.currentPrice();
                BigDecimal target = currentPrice.multiply(new BigDecimal("1.12")).setScale(4, RoundingMode.HALF_UP);
                BigDecimal stopLoss = currentPrice.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP);

                String sector = findSectorForSymbol(symbol);
                String reasoning = String.format("Sector rotation: %s sector score %d/100 (Q%d %d)",
                        sector, score, (date.getMonthValue() - 1) / 3 + 1, date.getYear());

                SignalStrength strength = score >= 80 ? SignalStrength.STRONG_BUY : SignalStrength.BUY;

                return List.of(new TradeSignal(
                        symbol, TradeSide.BUY, currentPrice, stopLoss, target,
                        strength, score, getName(), reasoning, snap, date
                ));
            } else if (score < 30) {
                BigDecimal currentPrice = snap.currentPrice();
                String sector = findSectorForSymbol(symbol);
                String reasoning = String.format("Sector rotation SELL: %s sector score %d/100", sector, score);

                return List.of(new TradeSignal(
                        symbol, TradeSide.SELL, currentPrice, null, null,
                        SignalStrength.SELL, 100 - score, getName(), reasoning, snap, date
                ));
            }

            return List.of();
        } catch (Exception e) {
            log.error("{}: sector rotation evaluation failed: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private int calculateSectorScore(IndicatorSnapshot snap) {
        int score = 0;
        int maxScore = 100;

        // RSI momentum: 30% weight
        if (snap.rsi14() != null) {
            double rsi = snap.rsi14().doubleValue();
            if (rsi >= 40 && rsi <= 70) {
                score += (int) (30 * (rsi - 30) / 40); // normalized
            } else if (rsi > 70) {
                score += 15; // overbought penalty
            }
        }

        // Price vs SMA20: 30% weight
        if (snap.sma20() != null && snap.currentPrice() != null && snap.sma20().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pctAboveSma = snap.currentPrice().subtract(snap.sma20())
                    .divide(snap.sma20(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (pctAboveSma.compareTo(BigDecimal.ZERO) > 0) {
                score += Math.min(30, pctAboveSma.intValue() * 3);
            }
        }

        // Volume trend: 20% weight
        if (snap.volumeRatio() != null) {
            if (snap.volumeRatio().compareTo(new BigDecimal("1.0")) > 0) {
                score += Math.min(20, snap.volumeRatio().intValue() * 10);
            }
        }

        // MACD direction: 20% weight
        if (snap.macdHistogram() != null) {
            if (snap.macdHistogram().compareTo(BigDecimal.ZERO) > 0) {
                score += 20;
            } else if (snap.macdLine() != null && snap.macdSignal() != null
                    && snap.macdLine().compareTo(snap.macdSignal()) > 0) {
                score += 10;
            }
        }

        return Math.min(score, maxScore);
    }

    private String findSectorForSymbol(String symbol) {
        for (Map.Entry<String, List<String>> entry : NGX_SECTORS.entrySet()) {
            if (entry.getValue().contains(symbol)) return entry.getKey();
        }
        return "Unknown";
    }

    @Override
    public List<String> getTargetSymbols() {
        List<String> symbols = new ArrayList<>();
        NGX_SECTORS.values().forEach(symbols::addAll);
        return symbols;
    }
}
