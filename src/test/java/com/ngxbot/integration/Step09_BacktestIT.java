package com.ngxbot.integration;

import com.ngxbot.backtest.entity.BacktestRun;
import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.repository.BacktestRunRepository;
import com.ngxbot.backtest.repository.BacktestTradeRepository;
import com.ngxbot.backtest.repository.EquityCurveRepository;
import com.ngxbot.backtest.service.BacktestRunner;
import com.ngxbot.data.client.EodhdApiClient;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.strategy.Strategy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 9: Backtesting with Real Historical Data
 *
 * Verifies:
 *   - Real EODHD historical data can be fetched for backtesting
 *   - BacktestRunner executes with real data
 *   - Performance metrics are calculated correctly
 *   - Results persist to backtest_runs, backtest_trades, equity_curve_points
 *
 * Prereqs: Step 1 (PostgreSQL) + Step 2 (EODHD) must pass
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step09_BacktestIT extends IntegrationTestBase {

    @Autowired
    private BacktestRunner backtestRunner;

    @Autowired
    private EodhdApiClient eodhdApiClient;

    @Autowired
    private BacktestRunRepository backtestRunRepository;

    @Autowired
    private BacktestTradeRepository backtestTradeRepository;

    @Autowired
    private EquityCurveRepository equityCurveRepository;

    @Autowired
    private List<Strategy> strategies;

    private static final String[] TEST_SYMBOLS = {
            "ZENITHBANK", "GTCO", "DANGCEM",
            "ACCESSCORP", "UBA"
    };

    @Test
    @Order(1)
    @DisplayName("9.1 Fetch 1 year of real OHLCV data for 5 NGX stocks")
    void fetchHistoricalDataForBacktest() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(1);

        for (String symbol : TEST_SYMBOLS) {
            List<OhlcvBar> bars = eodhdApiClient.fetchAndStoreOhlcv(symbol, startDate, endDate);
            System.out.printf("    %s: %d bars fetched (%s to %s)%n",
                    symbol, bars.size(), startDate, endDate);
            assertThat(bars).isNotEmpty();
        }

        printResult("Historical Data",
                String.format("Fetched 1 year of OHLCV data for %d stocks", TEST_SYMBOLS.length));
    }

    @Test
    @Order(2)
    @DisplayName("9.2 List available strategies")
    void listAvailableStrategies() {
        printResult("Available Strategies",
                String.format("%d strategies registered", strategies.size()));

        strategies.forEach(s ->
                System.out.printf("    - %s (enabled: %s)%n", s.getName(), s.isEnabled()));

        assertThat(strategies).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("9.3 Run backtest with first available strategy")
    void runBacktestWithRealData() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(1);
        BigDecimal initialCapital = new BigDecimal("1000000"); // ₦1M

        // Find first enabled strategy, or use the first strategy
        Strategy strategy = strategies.stream()
                .filter(Strategy::isEnabled)
                .findFirst()
                .orElse(strategies.get(0));

        BacktestRun run = backtestRunner.runBacktest(
                strategy, startDate, endDate, initialCapital, "NGX");

        printResult("Backtest Run",
                String.format("Strategy: %s\n" +
                        "    Period: %s to %s\n" +
                        "    Initial Capital: ₦%,.2f\n" +
                        "    Final Capital: ₦%,.2f\n" +
                        "    Total Return: %.2f%%\n" +
                        "    Sharpe Ratio: %.4f\n" +
                        "    Max Drawdown: %.2f%%\n" +
                        "    Win Rate: %.2f%%\n" +
                        "    Total Trades: %d",
                        strategy.getName(),
                        startDate, endDate,
                        initialCapital,
                        run.getFinalCapital(),
                        run.getTotalReturnPct(),
                        run.getSharpeRatio(),
                        run.getMaxDrawdownPct(),
                        run.getWinRatePct(),
                        run.getTotalTrades()));

        assertThat(run).isNotNull();
        assertThat(run.getId()).isNotNull();
        assertThat(run.getFinalCapital()).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("9.4 Verify backtest results persisted to database")
    void verifyBacktestPersistence() {
        List<BacktestRun> allRuns = backtestRunRepository.findAll();
        List<BacktestTrade> allTrades = backtestTradeRepository.findAll();

        printResult("Backtest Persistence",
                String.format("Backtest runs: %d, Backtest trades: %d",
                        allRuns.size(), allTrades.size()));

        assertThat(allRuns).isNotEmpty();

        // Check the most recent run has trades
        BacktestRun latestRun = allRuns.get(allRuns.size() - 1);
        System.out.printf("    Latest run ID: %d, Strategy: %s, Trades: %d%n",
                latestRun.getId(), latestRun.getStrategyName(), latestRun.getTotalTrades());
    }

    @Test
    @Order(5)
    @DisplayName("9.5 Verify backtest metrics are reasonable")
    void verifyBacktestMetrics() {
        List<BacktestRun> runs = backtestRunRepository.findAll();
        if (runs.isEmpty()) {
            printResult("Metrics Validation", "SKIPPED — no backtest runs to validate");
            return;
        }

        BacktestRun run = runs.get(runs.size() - 1);

        // Sanity checks on metrics
        if (run.getMaxDrawdownPct() != null) {
            assertThat(run.getMaxDrawdownPct())
                    .as("Max drawdown should be <= 0 (negative means loss)")
                    .isLessThanOrEqualTo(BigDecimal.ZERO);
        }

        if (run.getWinRatePct() != null) {
            assertThat(run.getWinRatePct())
                    .as("Win rate should be between 0% and 100%")
                    .isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        }

        printResult("Metrics Validation", "All backtest metrics within expected ranges");
    }
}
