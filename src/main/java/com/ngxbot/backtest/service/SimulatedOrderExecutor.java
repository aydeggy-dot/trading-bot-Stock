package com.ngxbot.backtest.service;

import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.common.model.TradeSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulatedOrderExecutor {

    private final OhlcvRepository ohlcvRepository;

    // Commission rates
    private static final BigDecimal NGX_COMMISSION_PCT = new BigDecimal("0.0015"); // 0.15% each side
    private static final BigDecimal US_COMMISSION_PCT = new BigDecimal("0.0005");   // 0.05% each side
    private static final BigDecimal SLIPPAGE_PCT = new BigDecimal("0.001");         // 0.1% slippage

    /**
     * Attempts to fill a BUY signal at next trading day's open + slippage.
     * Returns null if no next-day data exists or insufficient cash.
     */
    public BacktestTrade fillBuyOrder(TradeSignal signal, LocalDate signalDate, String market,
                                       BigDecimal availableCash, Long backtestRunId) {
        // Get next trading day's bar for fill price
        OhlcvBar fillBar = getNextTradingDayBar(signal.symbol(), signalDate);
        if (fillBar == null) {
            log.debug("[BACKTEST] No next-day data for {} after {}", signal.symbol(), signalDate);
            return null;
        }

        BigDecimal fillPrice = applySlippage(fillBar.getOpenPrice(), true);
        BigDecimal commissionRate = getCommissionRate(market);

        // Calculate max affordable quantity (accounting for commission)
        BigDecimal effectivePricePerShare = fillPrice.multiply(BigDecimal.ONE.add(commissionRate));
        int maxQuantity = availableCash.divide(effectivePricePerShare, 0, RoundingMode.DOWN).intValue();

        // Use signal's suggested quantity or cap to what we can afford
        int quantity = calculateQuantity(signal, availableCash, fillPrice, commissionRate);
        if (quantity <= 0) {
            log.debug("[BACKTEST] Insufficient cash for {} (need > {}, have {})",
                    signal.symbol(), fillPrice, availableCash);
            return null;
        }

        BigDecimal totalCost = fillPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal commission = totalCost.multiply(commissionRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal slippage = fillBar.getOpenPrice().multiply(SLIPPAGE_PCT)
                .multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);

        return BacktestTrade.builder()
                .backtestRunId(backtestRunId)
                .symbol(signal.symbol())
                .side("BUY")
                .entryDate(fillBar.getTradeDate())
                .entryPrice(fillPrice)
                .quantity(quantity)
                .commission(commission)
                .slippage(slippage)
                .signalStrength(signal.strength() != null ? signal.strength().name() : null)
                .confidenceScore(signal.confidenceScore())
                .isOpen(true)
                .build();
    }

    /**
     * Closes an open trade at next trading day's open + slippage.
     */
    public BacktestTrade fillSellOrder(BacktestTrade openTrade, LocalDate signalDate, String market) {
        OhlcvBar fillBar = getNextTradingDayBar(openTrade.getSymbol(), signalDate);
        if (fillBar == null) {
            return null;
        }

        BigDecimal fillPrice = applySlippage(fillBar.getOpenPrice(), false);
        return closeTrade(openTrade, fillPrice, fillBar.getTradeDate(), market, "SIGNAL_EXIT");
    }

    /**
     * Closes a trade at a specific price (stop-loss or target hit).
     */
    public BacktestTrade closeTrade(BacktestTrade openTrade, BigDecimal exitPrice,
                                     LocalDate exitDate, String market, String exitReason) {
        BigDecimal commissionRate = getCommissionRate(market);
        BigDecimal exitValue = exitPrice.multiply(BigDecimal.valueOf(openTrade.getQuantity()));
        BigDecimal exitCommission = exitValue.multiply(commissionRate).setScale(4, RoundingMode.HALF_UP);

        BigDecimal entryValue = openTrade.getEntryPrice().multiply(BigDecimal.valueOf(openTrade.getQuantity()));
        BigDecimal grossPnl = exitValue.subtract(entryValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCommission = openTrade.getCommission().add(exitCommission);
        BigDecimal netPnl = grossPnl.subtract(exitCommission).setScale(2, RoundingMode.HALF_UP);

        BigDecimal netPnlPct = BigDecimal.ZERO;
        if (entryValue.compareTo(BigDecimal.ZERO) != 0) {
            netPnlPct = netPnl.divide(entryValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        int holdingDays = (int) ChronoUnit.DAYS.between(openTrade.getEntryDate(), exitDate);

        openTrade.setExitDate(exitDate);
        openTrade.setExitPrice(exitPrice);
        openTrade.setGrossPnl(grossPnl);
        openTrade.setNetPnl(netPnl);
        openTrade.setNetPnlPct(netPnlPct);
        openTrade.setHoldingDays(holdingDays);
        openTrade.setCommission(totalCommission);
        openTrade.setExitReason(exitReason);
        openTrade.setIsOpen(false);
        return openTrade;
    }

    /**
     * Checks if stop-loss or target is hit during a trading day.
     */
    public String checkStopOrTarget(BacktestTrade openTrade, OhlcvBar bar) {
        // No stop/target yet — need to derive from signal
        // Check if low touches stop-loss (derived from entry - 8% default)
        BigDecimal stopLoss = openTrade.getEntryPrice().multiply(new BigDecimal("0.92"));
        BigDecimal target = openTrade.getEntryPrice().multiply(new BigDecimal("1.16")); // default 2:1 R/R

        if (bar.getLowPrice().compareTo(stopLoss) <= 0) {
            return "STOP_LOSS";
        }
        if (bar.getHighPrice().compareTo(target) >= 0) {
            return "TARGET_HIT";
        }
        return null;
    }

    /**
     * Calculates position size based on available cash and risk rules.
     * Max 15% of total capital per position.
     */
    private int calculateQuantity(TradeSignal signal, BigDecimal availableCash,
                                   BigDecimal fillPrice, BigDecimal commissionRate) {
        // Use max 15% of available cash per position
        BigDecimal maxPositionSize = availableCash.multiply(new BigDecimal("0.15"));
        BigDecimal effectivePrice = fillPrice.multiply(BigDecimal.ONE.add(commissionRate));
        return maxPositionSize.divide(effectivePrice, 0, RoundingMode.DOWN).intValue();
    }

    private OhlcvBar getNextTradingDayBar(String symbol, LocalDate signalDate) {
        // Look up to 5 days ahead for next trading day
        LocalDate searchEnd = signalDate.plusDays(5);
        List<OhlcvBar> bars = ohlcvRepository
                .findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, signalDate.plusDays(1), searchEnd);
        return bars.isEmpty() ? null : bars.get(0);
    }

    private BigDecimal applySlippage(BigDecimal price, boolean isBuy) {
        BigDecimal slippageAmount = price.multiply(SLIPPAGE_PCT);
        return isBuy ? price.add(slippageAmount) : price.subtract(slippageAmount);
    }

    private BigDecimal getCommissionRate(String market) {
        return "NGX".equals(market) ? NGX_COMMISSION_PCT : US_COMMISSION_PCT;
    }
}
