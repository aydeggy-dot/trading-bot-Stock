package com.ngxbot.backtest.service;

import com.ngxbot.backtest.entity.BacktestRun;
import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.entity.EquityCurvePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class PerformanceAnalyzer {

    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.10"); // 10% NGN T-bills
    private static final BigDecimal TRADING_DAYS_PER_YEAR = new BigDecimal("252");
    private static final MathContext MC = new MathContext(10);

    /**
     * Computes all performance metrics and populates the BacktestRun entity.
     */
    public BacktestRun analyze(BacktestRun run, List<BacktestTrade> trades,
                                List<EquityCurvePoint> equityCurve) {
        if (trades.isEmpty()) {
            run.setTotalTrades(0);
            run.setWinningTrades(0);
            run.setLosingTrades(0);
            run.setTotalReturnPct(BigDecimal.ZERO);
            run.setSharpeRatio(BigDecimal.ZERO);
            run.setMaxDrawdownPct(BigDecimal.ZERO);
            run.setWinRatePct(BigDecimal.ZERO);
            run.setProfitFactor(BigDecimal.ZERO);
            run.setAvgHoldingPeriodDays(BigDecimal.ZERO);
            run.setMaxConsecutiveLosses(0);
            run.setGrossProfit(BigDecimal.ZERO);
            run.setGrossLoss(BigDecimal.ZERO);
            run.setTotalCommissions(BigDecimal.ZERO);
            return run;
        }

        // Closed trades only for stats
        List<BacktestTrade> closedTrades = trades.stream()
                .filter(t -> !t.getIsOpen())
                .toList();

        // Trade counts
        int total = closedTrades.size();
        int winners = (int) closedTrades.stream()
                .filter(t -> t.getNetPnl() != null && t.getNetPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int losers = total - winners;

        run.setTotalTrades(total);
        run.setWinningTrades(winners);
        run.setLosingTrades(losers);

        // Win rate
        BigDecimal winRate = total > 0
                ? BigDecimal.valueOf(winners).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        run.setWinRatePct(winRate);

        // Gross profit and loss
        BigDecimal grossProfit = closedTrades.stream()
                .map(BacktestTrade::getNetPnl)
                .filter(pnl -> pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = closedTrades.stream()
                .map(BacktestTrade::getNetPnl)
                .filter(pnl -> pnl != null && pnl.compareTo(BigDecimal.ZERO) <= 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        run.setGrossProfit(grossProfit);
        run.setGrossLoss(grossLoss);

        // Profit factor
        BigDecimal profitFactor = grossLoss.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP)
                : grossProfit.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("999.0") : BigDecimal.ZERO;
        run.setProfitFactor(profitFactor);

        // Total commissions
        BigDecimal totalCommissions = closedTrades.stream()
                .map(t -> t.getCommission() != null ? t.getCommission() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        run.setTotalCommissions(totalCommissions);

        // Total return
        if (run.getFinalCapital() != null && run.getInitialCapital().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalReturn = run.getFinalCapital().subtract(run.getInitialCapital())
                    .divide(run.getInitialCapital(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            run.setTotalReturnPct(totalReturn);

            // Annualized return
            long days = ChronoUnit.DAYS.between(run.getStartDate(), run.getEndDate());
            if (days > 0) {
                double years = days / 365.25;
                double totalReturnDecimal = totalReturn.doubleValue() / 100.0;
                double annualized = (Math.pow(1 + totalReturnDecimal, 1.0 / years) - 1) * 100;
                run.setAnnualizedReturnPct(BigDecimal.valueOf(annualized).setScale(4, RoundingMode.HALF_UP));
            }
        }

        // Average holding period
        BigDecimal avgHolding = closedTrades.stream()
                .filter(t -> t.getHoldingDays() != null)
                .map(t -> BigDecimal.valueOf(t.getHoldingDays()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long countWithDays = closedTrades.stream().filter(t -> t.getHoldingDays() != null).count();
        if (countWithDays > 0) {
            avgHolding = avgHolding.divide(BigDecimal.valueOf(countWithDays), 2, RoundingMode.HALF_UP);
        }
        run.setAvgHoldingPeriodDays(avgHolding);

        // Max consecutive losses
        run.setMaxConsecutiveLosses(calculateMaxConsecutiveLosses(closedTrades));

        // Sharpe ratio from equity curve daily returns
        run.setSharpeRatio(calculateSharpeRatio(equityCurve));

        // Max drawdown from equity curve
        run.setMaxDrawdownPct(calculateMaxDrawdown(equityCurve));

        return run;
    }

    /**
     * Calculates annualized Sharpe ratio from daily equity curve returns.
     */
    public BigDecimal calculateSharpeRatio(List<EquityCurvePoint> equityCurve) {
        if (equityCurve.size() < 2) return BigDecimal.ZERO;

        List<BigDecimal> dailyReturns = equityCurve.stream()
                .filter(p -> p.getDailyReturnPct() != null)
                .map(EquityCurvePoint::getDailyReturnPct)
                .toList();

        if (dailyReturns.size() < 2) return BigDecimal.ZERO;

        // Mean daily return
        BigDecimal sum = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal meanReturn = sum.divide(BigDecimal.valueOf(dailyReturns.size()), 8, RoundingMode.HALF_UP);

        // Std dev of daily returns
        BigDecimal variance = dailyReturns.stream()
                .map(r -> r.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size() - 1), 8, RoundingMode.HALF_UP);

        double stdDev = Math.sqrt(variance.doubleValue());
        if (stdDev == 0) return BigDecimal.ZERO;

        // Daily risk-free rate
        BigDecimal dailyRf = RISK_FREE_RATE.divide(TRADING_DAYS_PER_YEAR, 8, RoundingMode.HALF_UP);
        BigDecimal excessReturn = meanReturn.subtract(dailyRf);

        // Annualized Sharpe = (mean_excess / std_dev) * sqrt(252)
        double sharpe = excessReturn.doubleValue() / stdDev * Math.sqrt(252);
        return BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculates maximum drawdown percentage from equity curve.
     */
    public BigDecimal calculateMaxDrawdown(List<EquityCurvePoint> equityCurve) {
        if (equityCurve.isEmpty()) return BigDecimal.ZERO;

        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (EquityCurvePoint point : equityCurve) {
            if (point.getPortfolioValue().compareTo(peak) > 0) {
                peak = point.getPortfolioValue();
            }
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peak.subtract(point.getPortfolioValue())
                        .divide(peak, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        return maxDrawdown;
    }

    private int calculateMaxConsecutiveLosses(List<BacktestTrade> trades) {
        int maxConsec = 0;
        int current = 0;
        List<BacktestTrade> sorted = trades.stream()
                .sorted(Comparator.comparing(BacktestTrade::getExitDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (BacktestTrade trade : sorted) {
            if (trade.getNetPnl() != null && trade.getNetPnl().compareTo(BigDecimal.ZERO) <= 0) {
                current++;
                maxConsec = Math.max(maxConsec, current);
            } else {
                current = 0;
            }
        }
        return maxConsec;
    }
}
