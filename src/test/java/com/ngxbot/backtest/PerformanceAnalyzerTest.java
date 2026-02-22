package com.ngxbot.backtest;

import com.ngxbot.backtest.entity.BacktestRun;
import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.entity.EquityCurvePoint;
import com.ngxbot.backtest.service.PerformanceAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceAnalyzerTest {

    private PerformanceAnalyzer analyzer;

    private static final LocalDate START = LocalDate.of(2025, 7, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        analyzer = new PerformanceAnalyzer();
    }

    private BacktestRun baseRun() {
        return BacktestRun.builder()
                .strategyName("TestStrategy")
                .market("NGX")
                .startDate(START)
                .endDate(END)
                .initialCapital(new BigDecimal("1000000"))
                .finalCapital(new BigDecimal("1150000"))
                .currency("NGN")
                .build();
    }

    private BacktestTrade closedTrade(BigDecimal netPnl, int holdingDays) {
        return BacktestTrade.builder()
                .backtestRunId(1L)
                .symbol("ZENITHBANK")
                .side("BUY")
                .entryDate(START)
                .exitDate(START.plusDays(holdingDays))
                .entryPrice(new BigDecimal("35.00"))
                .exitPrice(new BigDecimal("38.00"))
                .quantity(100)
                .commission(new BigDecimal("1.50"))
                .netPnl(netPnl)
                .holdingDays(holdingDays)
                .isOpen(false)
                .build();
    }

    @Test
    @DisplayName("analyze computes correct win rate: 3 winners out of 5 = 60%")
    void analyze_computesWinRate() {
        BacktestRun run = baseRun();
        List<BacktestTrade> trades = List.of(
                closedTrade(new BigDecimal("500"), 10),
                closedTrade(new BigDecimal("300"), 5),
                closedTrade(new BigDecimal("-200"), 8),
                closedTrade(new BigDecimal("100"), 3),
                closedTrade(new BigDecimal("-400"), 15)
        );

        List<EquityCurvePoint> curve = List.of();

        BacktestRun result = analyzer.analyze(run, trades, curve);

        assertThat(result.getTotalTrades()).isEqualTo(5);
        assertThat(result.getWinningTrades()).isEqualTo(3);
        assertThat(result.getLosingTrades()).isEqualTo(2);
        assertThat(result.getWinRatePct()).isEqualByComparingTo(new BigDecimal("60.0000"));
    }

    @Test
    @DisplayName("analyze computes profit factor: gross_profit / gross_loss")
    void analyze_computesProfitFactor() {
        BacktestRun run = baseRun();
        List<BacktestTrade> trades = List.of(
                closedTrade(new BigDecimal("1000"), 10),
                closedTrade(new BigDecimal("500"), 5),
                closedTrade(new BigDecimal("-300"), 7)
        );

        BacktestRun result = analyzer.analyze(run, trades, List.of());

        // Gross profit = 1000 + 500 = 1500, Gross loss = 300
        assertThat(result.getGrossProfit()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.getGrossLoss()).isEqualByComparingTo(new BigDecimal("300"));
        // Profit factor = 1500 / 300 = 5.0
        assertThat(result.getProfitFactor()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("analyze returns zeroes for empty trade list")
    void analyze_emptyTrades() {
        BacktestRun run = baseRun();
        BacktestRun result = analyzer.analyze(run, List.of(), List.of());

        assertThat(result.getTotalTrades()).isEqualTo(0);
        assertThat(result.getWinRatePct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getProfitFactor()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("analyze computes total return percentage correctly: (1150000-1000000)/1000000 = 15%")
    void analyze_totalReturn() {
        BacktestRun run = baseRun();
        List<BacktestTrade> trades = List.of(closedTrade(new BigDecimal("150000"), 30));

        BacktestRun result = analyzer.analyze(run, trades, List.of());

        assertThat(result.getTotalReturnPct()).isEqualByComparingTo(new BigDecimal("15.0000"));
    }

    @Test
    @DisplayName("analyze computes average holding period: (10+5+15)/3 = 10.0 days")
    void analyze_avgHoldingPeriod() {
        BacktestRun run = baseRun();
        List<BacktestTrade> trades = List.of(
                closedTrade(new BigDecimal("100"), 10),
                closedTrade(new BigDecimal("200"), 5),
                closedTrade(new BigDecimal("-50"), 15)
        );

        BacktestRun result = analyzer.analyze(run, trades, List.of());

        assertThat(result.getAvgHoldingPeriodDays()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("analyze computes max consecutive losses: [W, L, L, L, W] = 3")
    void analyze_maxConsecutiveLosses() {
        BacktestRun run = baseRun();
        List<BacktestTrade> trades = List.of(
                closedTrade(new BigDecimal("500"), 2),    // W
                closedTrade(new BigDecimal("-100"), 3),   // L
                closedTrade(new BigDecimal("-200"), 4),   // L
                closedTrade(new BigDecimal("-50"), 5),    // L
                closedTrade(new BigDecimal("300"), 6)     // W
        );

        BacktestRun result = analyzer.analyze(run, trades, List.of());

        assertThat(result.getMaxConsecutiveLosses()).isEqualTo(3);
    }

    @Test
    @DisplayName("calculateMaxDrawdown from equity curve: peak 1100 to trough 900 = 18.18%")
    void calculateMaxDrawdown_correctValue() {
        List<EquityCurvePoint> curve = List.of(
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1000000")).build(),
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1100000")).build(),
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("900000")).build(),
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1050000")).build()
        );

        BigDecimal maxDD = analyzer.calculateMaxDrawdown(curve);

        // (1100000 - 900000) / 1100000 = 18.1818%
        assertThat(maxDD).isGreaterThan(new BigDecimal("18.0"));
        assertThat(maxDD).isLessThan(new BigDecimal("19.0"));
    }

    @Test
    @DisplayName("calculateMaxDrawdown returns ZERO for monotonically increasing equity")
    void calculateMaxDrawdown_noDrawdown() {
        List<EquityCurvePoint> curve = List.of(
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1000000")).build(),
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1050000")).build(),
                EquityCurvePoint.builder().portfolioValue(new BigDecimal("1100000")).build()
        );

        BigDecimal maxDD = analyzer.calculateMaxDrawdown(curve);

        assertThat(maxDD).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("analyze computes total commissions from all trades")
    void analyze_totalCommissions() {
        BacktestRun run = baseRun();

        BacktestTrade t1 = closedTrade(new BigDecimal("500"), 10);
        t1.setCommission(new BigDecimal("25.00"));
        BacktestTrade t2 = closedTrade(new BigDecimal("300"), 5);
        t2.setCommission(new BigDecimal("15.00"));

        BacktestRun result = analyzer.analyze(run, List.of(t1, t2), List.of());

        assertThat(result.getTotalCommissions()).isEqualByComparingTo(new BigDecimal("40.00"));
    }
}
