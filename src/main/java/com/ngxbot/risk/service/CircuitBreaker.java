package com.ngxbot.risk.service;

import com.ngxbot.config.RiskProperties;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.strategy.StrategyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Halts SATELLITE trading when losses exceed configurable thresholds.
 * <p>
 * Rules:
 * <ul>
 *   <li>Daily loss >= 5% of portfolio -> halt SATELLITE trading for the day</li>
 *   <li>Weekly loss >= 10% of portfolio -> halt SATELLITE trading until Monday</li>
 *   <li>Cross-market: both NGX and US losing > 3% in a day -> halt SATELLITE</li>
 * </ul>
 * <p>
 * CORE positions are never halted by the circuit breaker. They represent the
 * stable foundation of the portfolio and continue operating through drawdowns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreaker {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal CROSS_MARKET_THRESHOLD_PCT = new BigDecimal("3.0");

    private final RiskProperties riskProperties;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * Checks whether the circuit breaker is active for a given pool.
     * <p>
     * CORE pool is NEVER halted. SATELLITE is halted if any circuit breaker
     * condition is met (daily loss, weekly loss, or cross-market).
     *
     * @param pool CORE or SATELLITE
     * @return true if trading should be halted for this pool
     */
    public boolean isCircuitBroken(StrategyPool pool) {
        if (pool == StrategyPool.CORE) {
            return false;
        }

        boolean dailyBroken = isDailyCircuitBroken();
        boolean weeklyBroken = isWeeklyCircuitBroken();
        boolean crossMarketBroken = isCrossMarketCircuitBroken();

        if (dailyBroken || weeklyBroken || crossMarketBroken) {
            log.error("CIRCUIT BREAKER ACTIVE for SATELLITE pool | daily={}, weekly={}, crossMarket={}",
                    dailyBroken, weeklyBroken, crossMarketBroken);
            return true;
        }

        return false;
    }

    /**
     * Checks whether the daily loss threshold has been breached.
     * Threshold: 5% daily loss (configurable via risk.daily-loss-circuit-breaker-pct).
     *
     * @return true if daily loss exceeds the threshold
     */
    public boolean isDailyCircuitBroken() {
        Optional<PortfolioSnapshot> latestOpt = portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc();
        if (latestOpt.isEmpty()) {
            log.debug("No portfolio snapshot available for daily circuit breaker check");
            return false;
        }

        PortfolioSnapshot latest = latestOpt.get();
        BigDecimal dailyPnlPct = latest.getDailyPnlPct();

        if (dailyPnlPct == null) {
            return false;
        }

        // dailyPnlPct is stored as a percentage (e.g., -5.2 means 5.2% loss)
        BigDecimal threshold = riskProperties.getDailyLossCircuitBreakerPct().negate();
        boolean broken = dailyPnlPct.compareTo(threshold) <= 0;

        if (broken) {
            log.error("DAILY CIRCUIT BREAKER TRIGGERED: daily P&L {}% exceeds -{}% threshold",
                    dailyPnlPct, riskProperties.getDailyLossCircuitBreakerPct());
        }

        return broken;
    }

    /**
     * Checks whether the weekly loss threshold has been breached.
     * Threshold: 10% weekly loss (configurable via risk.weekly-loss-circuit-breaker-pct).
     * <p>
     * Calculates weekly performance by comparing today's portfolio value to
     * the most recent Monday snapshot.
     *
     * @return true if weekly loss exceeds the threshold
     */
    public boolean isWeeklyCircuitBroken() {
        Optional<PortfolioSnapshot> latestOpt = portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc();
        if (latestOpt.isEmpty()) {
            return false;
        }

        PortfolioSnapshot latest = latestOpt.get();

        // Find the Monday of the current week
        LocalDate today = latest.getSnapshotDate();
        LocalDate monday = today;
        while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            monday = monday.minusDays(1);
        }

        // Get snapshot from the start of the week
        List<PortfolioSnapshot> weekSnapshots = portfolioSnapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(monday, today);

        if (weekSnapshots.isEmpty()) {
            return false;
        }

        PortfolioSnapshot weekStart = weekSnapshots.get(0);
        BigDecimal startValue = weekStart.getTotalValue();

        if (startValue.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal currentValue = latest.getTotalValue();
        BigDecimal weeklyChangePct = currentValue.subtract(startValue)
                .multiply(HUNDRED)
                .divide(startValue, 4, java.math.RoundingMode.HALF_UP);

        BigDecimal threshold = riskProperties.getWeeklyLossCircuitBreakerPct().negate();
        boolean broken = weeklyChangePct.compareTo(threshold) <= 0;

        if (broken) {
            log.error("WEEKLY CIRCUIT BREAKER TRIGGERED: weekly change {}% exceeds -{}% threshold. "
                            + "Start value: {}, Current value: {}",
                    weeklyChangePct, riskProperties.getWeeklyLossCircuitBreakerPct(),
                    startValue.toPlainString(), currentValue.toPlainString());
        }

        return broken;
    }

    /**
     * Checks for a cross-market circuit breaker: both markets (NGX and US)
     * losing more than 3% in a single day signals a global risk event.
     * <p>
     * This is a simplified implementation that checks the overall portfolio
     * daily P&L against the cross-market threshold. In a full implementation,
     * per-market P&L tracking would provide more granular detection.
     *
     * @return true if cross-market conditions indicate a global risk event
     */
    public boolean isCrossMarketCircuitBroken() {
        Optional<PortfolioSnapshot> latestOpt = portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc();
        if (latestOpt.isEmpty()) {
            return false;
        }

        PortfolioSnapshot latest = latestOpt.get();
        BigDecimal dailyPnlPct = latest.getDailyPnlPct();

        if (dailyPnlPct == null) {
            return false;
        }

        // If overall portfolio is down more than the cross-market threshold,
        // treat it as a global event affecting both markets
        BigDecimal threshold = CROSS_MARKET_THRESHOLD_PCT.negate();
        boolean broken = dailyPnlPct.compareTo(threshold) <= 0;

        if (broken) {
            log.error("CROSS-MARKET CIRCUIT BREAKER TRIGGERED: daily P&L {}% suggests global risk event "
                    + "(threshold: -{}%)", dailyPnlPct, CROSS_MARKET_THRESHOLD_PCT);
        }

        return broken;
    }

    /**
     * Periodic circuit breaker check. Runs every 10 minutes during combined
     * NGX + US trading hours (10:00-22:00 WAT) on weekdays.
     * <p>
     * Logs the current circuit breaker state for monitoring and alerting.
     */
    @Scheduled(cron = "0 */10 10-22 * * MON-FRI", zone = "Africa/Lagos")
    public void periodicCircuitCheck() {
        boolean dailyBroken = isDailyCircuitBroken();
        boolean weeklyBroken = isWeeklyCircuitBroken();
        boolean crossMarketBroken = isCrossMarketCircuitBroken();

        if (dailyBroken || weeklyBroken || crossMarketBroken) {
            log.error("PERIODIC CIRCUIT CHECK: SATELLITE trading HALTED | "
                            + "daily={}, weekly={}, crossMarket={}",
                    dailyBroken, weeklyBroken, crossMarketBroken);
        } else {
            log.debug("Periodic circuit check: all clear. SATELLITE trading allowed.");
        }
    }
}
