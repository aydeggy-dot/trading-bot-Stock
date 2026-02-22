package com.ngxbot.backtest.service;

import com.ngxbot.backtest.entity.BacktestRun;
import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.entity.EquityCurvePoint;
import com.ngxbot.backtest.repository.BacktestRunRepository;
import com.ngxbot.backtest.repository.BacktestTradeRepository;
import com.ngxbot.backtest.repository.EquityCurveRepository;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.common.model.TradeSide;
import com.ngxbot.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BacktestRunner {

    private final OhlcvRepository ohlcvRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final EquityCurveRepository equityCurveRepository;
    private final SimulatedOrderExecutor orderExecutor;
    private final PerformanceAnalyzer performanceAnalyzer;

    private static final int MAX_OPEN_POSITIONS = 10;

    /**
     * Runs a complete backtest for a strategy over the given date range.
     */
    @Transactional
    public BacktestRun runBacktest(Strategy strategy, LocalDate startDate, LocalDate endDate,
                                    BigDecimal initialCapital, String market) {
        String currency = "NGX".equals(market) ? "NGN" : "USD";
        log.info("[BACKTEST] Starting: strategy={}, market={}, period={} to {}, capital={}",
                strategy.getName(), market, startDate, endDate, initialCapital);

        // Create run record
        BacktestRun run = BacktestRun.builder()
                .strategyName(strategy.getName())
                .market(market)
                .startDate(startDate)
                .endDate(endDate)
                .initialCapital(initialCapital)
                .currency(currency)
                .status("RUNNING")
                .build();
        run = backtestRunRepository.save(run);

        try {
            BacktestState state = new BacktestState(initialCapital);
            List<BacktestTrade> allTrades = new ArrayList<>();
            List<EquityCurvePoint> equityCurve = new ArrayList<>();

            // Get all trading dates in range
            List<LocalDate> tradingDates = getTradingDates(startDate, endDate, strategy.getTargetSymbols());

            for (LocalDate date : tradingDates) {
                // 1. Check stop-loss / target for open positions
                processOpenPositions(state, date, market, allTrades);

                // 2. Generate signals for this date
                List<TradeSignal> signals = generateSignals(strategy, date);

                // 3. Process SELL signals first (free up cash)
                for (TradeSignal signal : signals) {
                    if (signal.side() == TradeSide.SELL) {
                        processSellSignal(signal, date, market, state, allTrades);
                    }
                }

                // 4. Process BUY signals
                for (TradeSignal signal : signals) {
                    if (signal.side() == TradeSide.BUY && state.openPositions.size() < MAX_OPEN_POSITIONS) {
                        // Skip if already holding this symbol
                        boolean alreadyHeld = state.openPositions.values().stream()
                                .anyMatch(t -> t.getSymbol().equals(signal.symbol()));
                        if (!alreadyHeld) {
                            processBuySignal(signal, date, market, state, allTrades, run.getId());
                        }
                    }
                }

                // 5. Record equity curve point
                BigDecimal positionsValue = calculatePositionsValue(state, date);
                BigDecimal portfolioValue = state.cash.add(positionsValue);
                BigDecimal dailyReturn = BigDecimal.ZERO;
                if (!equityCurve.isEmpty()) {
                    BigDecimal prevValue = equityCurve.get(equityCurve.size() - 1).getPortfolioValue();
                    if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                        dailyReturn = portfolioValue.subtract(prevValue)
                                .divide(prevValue, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
                    }
                }

                BigDecimal drawdownPct = BigDecimal.ZERO;
                BigDecimal peak = equityCurve.stream()
                        .map(EquityCurvePoint::getPortfolioValue)
                        .max(Comparator.naturalOrder())
                        .orElse(initialCapital);
                if (peak.compareTo(BigDecimal.ZERO) > 0 && portfolioValue.compareTo(peak) < 0) {
                    drawdownPct = peak.subtract(portfolioValue)
                            .divide(peak, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                }

                EquityCurvePoint point = EquityCurvePoint.builder()
                        .backtestRunId(run.getId())
                        .tradeDate(date)
                        .portfolioValue(portfolioValue)
                        .cashBalance(state.cash)
                        .positionsValue(positionsValue)
                        .drawdownPct(drawdownPct)
                        .dailyReturnPct(dailyReturn)
                        .build();
                equityCurve.add(point);
            }

            // Close any remaining open positions at end of backtest
            closeAllOpenPositions(state, endDate, market, allTrades);

            // Calculate final capital
            BigDecimal finalPositionsValue = calculatePositionsValue(state, endDate);
            BigDecimal finalCapitalValue = state.cash.add(finalPositionsValue);
            run.setFinalCapital(finalCapitalValue);

            // Persist trades and equity curve
            backtestTradeRepository.saveAll(allTrades);
            equityCurveRepository.saveAll(equityCurve);

            // Analyze performance
            run = performanceAnalyzer.analyze(run, allTrades, equityCurve);
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());

            log.info("[BACKTEST] Completed: strategy={}, trades={}, return={}%, sharpe={}, maxDD={}%",
                    strategy.getName(), run.getTotalTrades(), run.getTotalReturnPct(),
                    run.getSharpeRatio(), run.getMaxDrawdownPct());

        } catch (Exception e) {
            log.error("[BACKTEST] Failed: strategy={}", strategy.getName(), e);
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
        }

        return backtestRunRepository.save(run);
    }

    private List<TradeSignal> generateSignals(Strategy strategy, LocalDate date) {
        List<TradeSignal> allSignals = new ArrayList<>();
        for (String symbol : strategy.getTargetSymbols()) {
            try {
                List<TradeSignal> signals = strategy.evaluate(symbol, date);
                if (signals != null) {
                    allSignals.addAll(signals);
                }
            } catch (Exception e) {
                log.trace("[BACKTEST] Signal error for {} on {}: {}", symbol, date, e.getMessage());
            }
        }
        return allSignals;
    }

    private void processBuySignal(TradeSignal signal, LocalDate date, String market,
                                   BacktestState state, List<BacktestTrade> allTrades, Long runId) {
        BacktestTrade trade = orderExecutor.fillBuyOrder(signal, date, market, state.cash, runId);
        if (trade != null) {
            BigDecimal cost = trade.getEntryPrice().multiply(BigDecimal.valueOf(trade.getQuantity()))
                    .add(trade.getCommission());
            state.cash = state.cash.subtract(cost);
            state.openPositions.put(trade.getSymbol(), trade);
            allTrades.add(trade);
            log.debug("[BACKTEST] BUY {} qty={} @ {} on {}",
                    trade.getSymbol(), trade.getQuantity(), trade.getEntryPrice(), trade.getEntryDate());
        }
    }

    private void processSellSignal(TradeSignal signal, LocalDate date, String market,
                                    BacktestState state, List<BacktestTrade> allTrades) {
        BacktestTrade openTrade = state.openPositions.get(signal.symbol());
        if (openTrade != null) {
            BacktestTrade closed = orderExecutor.fillSellOrder(openTrade, date, market);
            if (closed != null) {
                BigDecimal proceeds = closed.getExitPrice().multiply(BigDecimal.valueOf(closed.getQuantity()))
                        .subtract(closed.getCommission().subtract(openTrade.getCommission()));
                state.cash = state.cash.add(proceeds);
                state.openPositions.remove(signal.symbol());
                log.debug("[BACKTEST] SELL {} qty={} @ {} pnl={}",
                        closed.getSymbol(), closed.getQuantity(), closed.getExitPrice(), closed.getNetPnl());
            }
        }
    }

    private void processOpenPositions(BacktestState state, LocalDate date, String market,
                                       List<BacktestTrade> allTrades) {
        List<String> toClose = new ArrayList<>();
        for (Map.Entry<String, BacktestTrade> entry : state.openPositions.entrySet()) {
            BacktestTrade openTrade = entry.getValue();
            List<OhlcvBar> bars = ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                    openTrade.getSymbol(), date, date);
            if (!bars.isEmpty()) {
                OhlcvBar bar = bars.get(0);
                String exitReason = orderExecutor.checkStopOrTarget(openTrade, bar);
                if (exitReason != null) {
                    BigDecimal exitPrice = "STOP_LOSS".equals(exitReason)
                            ? openTrade.getEntryPrice().multiply(new BigDecimal("0.92"))
                            : openTrade.getEntryPrice().multiply(new BigDecimal("1.16"));
                    orderExecutor.closeTrade(openTrade, exitPrice, date, market, exitReason);
                    BigDecimal proceeds = exitPrice.multiply(BigDecimal.valueOf(openTrade.getQuantity()));
                    state.cash = state.cash.add(proceeds);
                    toClose.add(entry.getKey());
                }
            }
        }
        toClose.forEach(state.openPositions::remove);
    }

    private void closeAllOpenPositions(BacktestState state, LocalDate endDate, String market,
                                        List<BacktestTrade> allTrades) {
        for (BacktestTrade openTrade : new ArrayList<>(state.openPositions.values())) {
            // Close at last available price
            List<OhlcvBar> bars = ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                    openTrade.getSymbol(), endDate.minusDays(5), endDate);
            if (!bars.isEmpty()) {
                OhlcvBar lastBar = bars.get(bars.size() - 1);
                orderExecutor.closeTrade(openTrade, lastBar.getClosePrice(), endDate, market, "END_OF_BACKTEST");
                BigDecimal proceeds = lastBar.getClosePrice().multiply(BigDecimal.valueOf(openTrade.getQuantity()));
                state.cash = state.cash.add(proceeds);
            }
            state.openPositions.remove(openTrade.getSymbol());
        }
    }

    private BigDecimal calculatePositionsValue(BacktestState state, LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (BacktestTrade trade : state.openPositions.values()) {
            List<OhlcvBar> bars = ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                    trade.getSymbol(), date.minusDays(5), date);
            if (!bars.isEmpty()) {
                OhlcvBar latestBar = bars.get(bars.size() - 1);
                total = total.add(latestBar.getClosePrice().multiply(BigDecimal.valueOf(trade.getQuantity())));
            } else {
                // Fallback: use entry price
                total = total.add(trade.getEntryPrice().multiply(BigDecimal.valueOf(trade.getQuantity())));
            }
        }
        return total;
    }

    private List<LocalDate> getTradingDates(LocalDate start, LocalDate end, List<String> symbols) {
        // Use actual OHLCV data dates as trading calendar
        if (symbols == null || symbols.isEmpty()) return List.of();
        String referenceSymbol = symbols.get(0);
        return ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(referenceSymbol, start, end)
                .stream()
                .map(OhlcvBar::getTradeDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Internal mutable state used during a backtest run */
    private static class BacktestState {
        BigDecimal cash;
        Map<String, BacktestTrade> openPositions = new LinkedHashMap<>();

        BacktestState(BigDecimal initialCash) {
            this.cash = initialCash;
        }
    }
}
