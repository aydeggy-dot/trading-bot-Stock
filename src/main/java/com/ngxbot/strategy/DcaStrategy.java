package com.ngxbot.strategy;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.StrategyProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DcaStrategy implements Strategy {

    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;

    @Override
    public String getName() {
        return "DOLLAR_COST_AVERAGING";
    }

    @Override
    public boolean isEnabled() {
        return strategyProperties.getDca().isEnabled();
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

        int ngxDay = strategyProperties.getDca().getNgxExecutionDay();
        int usDay = strategyProperties.getDca().getUsExecutionDay();

        // Only generate signals on execution days (or within 2 days for non-trading day adjustment)
        boolean isNgxDay = date.getDayOfMonth() >= ngxDay && date.getDayOfMonth() <= ngxDay + 2;
        boolean isUsDay = date.getDayOfMonth() >= usDay && date.getDayOfMonth() <= usDay + 2;

        if (!isNgxDay && !isUsDay) {
            return List.of();
        }

        String month = date.getMonth().toString();
        String reasoning = String.format("Systematic DCA allocation for %s %d", month, date.getYear());

        return List.of(new TradeSignal(
                symbol, TradeSide.BUY, BigDecimal.ZERO, null, null,
                SignalStrength.BUY, 60, getName(), reasoning, null, date
        ));
    }

    @Override
    public List<String> getTargetSymbols() {
        // Top diversified picks from watchlist
        List<String> symbols = new ArrayList<>();
        List<String> largeCaps = tradingProperties.getWatchlist().getLargeCaps();
        List<String> etfs = tradingProperties.getWatchlist().getEtfs();

        // Select top 3 large caps and top 2 ETFs for diversification
        for (int i = 0; i < Math.min(3, largeCaps.size()); i++) {
            symbols.add(largeCaps.get(i));
        }
        for (int i = 0; i < Math.min(2, etfs.size()); i++) {
            symbols.add(etfs.get(i));
        }
        return symbols;
    }
}
