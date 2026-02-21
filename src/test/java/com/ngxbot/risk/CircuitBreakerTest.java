package com.ngxbot.risk;

import com.ngxbot.config.RiskProperties;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.service.CircuitBreaker;
import com.ngxbot.strategy.StrategyPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerTest {

    @Mock private RiskProperties riskProperties;
    @Mock private PortfolioSnapshotRepository portfolioSnapshotRepository;

    private CircuitBreaker circuitBreaker;

    // Tuesday 2026-01-20 -- Monday would be 2026-01-19
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 20);
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 19);

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker(riskProperties, portfolioSnapshotRepository);
    }

    private PortfolioSnapshot snapshotOn(LocalDate date, BigDecimal totalValue,
                                          BigDecimal dailyPnlPct, BigDecimal weeklyPnl) {
        return PortfolioSnapshot.builder()
                .snapshotDate(date)
                .totalValue(totalValue)
                .cashBalance(new BigDecimal("300000"))
                .equityValue(totalValue.subtract(new BigDecimal("300000")))
                .dailyPnl(dailyPnlPct.multiply(new BigDecimal("10000")))
                .dailyPnlPct(dailyPnlPct)
                .weeklyPnl(weeklyPnl)
                .build();
    }

    private void mockSnapshotsForWeekly(BigDecimal mondayTotal, BigDecimal todayTotal,
                                         BigDecimal todayDailyPnlPct) {
        // Latest snapshot (today)
        PortfolioSnapshot todaySnapshot = snapshotOn(TODAY, todayTotal,
                todayDailyPnlPct, BigDecimal.ZERO);
        when(portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(todaySnapshot));

        // Monday snapshot for weekly comparison
        PortfolioSnapshot mondaySnapshot = snapshotOn(MONDAY, mondayTotal,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(portfolioSnapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDateAsc(
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(mondaySnapshot, todaySnapshot));
    }

    @Test
    @DisplayName("Circuit NOT broken when daily loss is small (< 5%)")
    void circuitNotBrokenOnSmallLoss() {
        when(riskProperties.getDailyLossCircuitBreakerPct()).thenReturn(new BigDecimal("5.0"));
        when(riskProperties.getWeeklyLossCircuitBreakerPct()).thenReturn(new BigDecimal("10.0"));

        // Daily loss of -2%, portfolio value 1,000,000 -> Monday was 1,020,000 -> ~2% weekly loss
        mockSnapshotsForWeekly(new BigDecimal("1020000"), new BigDecimal("1000000"),
                new BigDecimal("-2.0"));

        assertThat(circuitBreaker.isDailyCircuitBroken()).isFalse();
        assertThat(circuitBreaker.isWeeklyCircuitBroken()).isFalse();
    }

    @Test
    @DisplayName("Daily circuit broken on 5% loss")
    void dailyCircuitBrokenOn5PercentLoss() {
        when(riskProperties.getDailyLossCircuitBreakerPct()).thenReturn(new BigDecimal("5.0"));

        // Daily loss of -5.5% exceeds 5% threshold
        PortfolioSnapshot snapshot = snapshotOn(TODAY, new BigDecimal("1000000"),
                new BigDecimal("-5.5"), new BigDecimal("-20000"));
        when(portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(snapshot));

        assertThat(circuitBreaker.isDailyCircuitBroken()).isTrue();
    }

    @Test
    @DisplayName("Weekly circuit broken on 10% loss")
    void weeklyCircuitBrokenOn10PercentLoss() {
        when(riskProperties.getWeeklyLossCircuitBreakerPct()).thenReturn(new BigDecimal("10.0"));

        // Monday portfolio was 1,000,000; now it's 890,000 => -11% weekly loss > 10%
        mockSnapshotsForWeekly(new BigDecimal("1000000"), new BigDecimal("890000"),
                new BigDecimal("-3.0"));

        assertThat(circuitBreaker.isWeeklyCircuitBroken()).isTrue();
    }

    @Test
    @DisplayName("Circuit breaker only applies to SATELLITE pool")
    void circuitBreakerOnlyAppliesToSatellite() {
        when(riskProperties.getDailyLossCircuitBreakerPct()).thenReturn(new BigDecimal("5.0"));
        when(riskProperties.getWeeklyLossCircuitBreakerPct()).thenReturn(new BigDecimal("10.0"));

        // Severe daily loss (-7%) that trips the daily circuit breaker
        // Monday 1,000,000 -> Today 880,000 = -12% weekly loss too
        mockSnapshotsForWeekly(new BigDecimal("1000000"), new BigDecimal("880000"),
                new BigDecimal("-7.0"));

        // SATELLITE pool should be broken
        assertThat(circuitBreaker.isCircuitBroken(StrategyPool.SATELLITE)).isTrue();

        // CORE pool should NOT be affected by circuit breaker
        assertThat(circuitBreaker.isCircuitBroken(StrategyPool.CORE)).isFalse();
    }

    @Test
    @DisplayName("CORE pool continues trading even when circuit is broken")
    void corePoolContinuesWhenCircuitBroken() {
        when(riskProperties.getDailyLossCircuitBreakerPct()).thenReturn(new BigDecimal("5.0"));
        when(riskProperties.getWeeklyLossCircuitBreakerPct()).thenReturn(new BigDecimal("10.0"));

        // Both daily AND weekly circuits tripped
        // Daily -6%, Monday 1,000,000 -> Today 850,000 = -15% weekly
        mockSnapshotsForWeekly(new BigDecimal("1000000"), new BigDecimal("850000"),
                new BigDecimal("-6.0"));

        // Daily circuit should be broken
        assertThat(circuitBreaker.isDailyCircuitBroken()).isTrue();
        // Weekly circuit should be broken
        assertThat(circuitBreaker.isWeeklyCircuitBroken()).isTrue();

        // But CORE pool is exempt from circuit breaker
        assertThat(circuitBreaker.isCircuitBroken(StrategyPool.CORE)).isFalse();
        // While SATELLITE is halted
        assertThat(circuitBreaker.isCircuitBroken(StrategyPool.SATELLITE)).isTrue();
    }
}
