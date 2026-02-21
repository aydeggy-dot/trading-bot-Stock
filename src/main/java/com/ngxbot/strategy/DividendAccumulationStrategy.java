package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.CorporateAction;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.CorporateActionRepository;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
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
public class DividendAccumulationStrategy implements Strategy {

    private final StrategyProperties strategyProperties;
    private final OhlcvRepository ohlcvRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final TradingProperties tradingProperties;

    @Override
    public String getName() {
        return "DIVIDEND_ACCUMULATION";
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getDividendAccumulation().isEnabled();
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
            List<OhlcvBar> latestBars = ohlcvRepository.findBySymbolOrderByTradeDateDesc(symbol);
            if (latestBars.isEmpty() || latestBars.get(0).getClosePrice() == null) return List.of();

            BigDecimal currentPrice = latestBars.get(0).getClosePrice();
            if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) return List.of();

            // Look for dividend history in corporate actions
            List<CorporateAction> dividends = corporateActionRepository
                    .findBySymbolOrderByExDateDesc(symbol);

            if (dividends.isEmpty()) return List.of();

            // Calculate trailing yield from most recent dividend
            BigDecimal lastDividend = dividends.get(0).getValue();
            if (lastDividend == null || lastDividend.compareTo(BigDecimal.ZERO) <= 0) return List.of();

            BigDecimal trailingYield = lastDividend
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            BigDecimal minYield = strategyProperties.getDividendAccumulation().getNgxMinTrailingYieldPct();

            if (trailingYield.compareTo(minYield) < 0) {
                log.debug("{}: trailing yield {}% below threshold {}%", symbol, trailingYield, minYield);
                return List.of();
            }

            // Check dividend consistency (at least 2 dividends in history)
            if (dividends.size() < 2) {
                log.debug("{}: insufficient dividend history ({})", symbol, dividends.size());
                return List.of();
            }

            int confidence = 65;
            if (trailingYield.compareTo(new BigDecimal("8.0")) > 0) confidence += 10;
            if (dividends.size() >= 5) confidence += 5;
            confidence = Math.min(confidence, 100);

            BigDecimal stopLoss = currentPrice.multiply(new BigDecimal("0.90")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal target = currentPrice.multiply(new BigDecimal("1.15")).setScale(4, RoundingMode.HALF_UP);

            String reasoning = String.format("Dividend yield %.2f%% exceeds %.1f%% threshold. %d historical dividends.",
                    trailingYield, minYield, dividends.size());

            return List.of(new TradeSignal(
                    symbol, TradeSide.BUY, currentPrice, stopLoss, target,
                    SignalStrength.BUY, confidence, getName(), reasoning, null, date
            ));
        } catch (Exception e) {
            log.error("{}: evaluation failed: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> getTargetSymbols() {
        List<String> symbols = new ArrayList<>(tradingProperties.getWatchlist().getLargeCaps());
        return symbols;
    }
}
