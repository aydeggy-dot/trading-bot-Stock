package com.ngxbot.risk;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.config.RiskProperties;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.entity.RiskCheckResult;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.risk.service.RiskManager;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.StrategyMarket;
import com.ngxbot.strategy.StrategyPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskManagerTest {

    @Mock private RiskProperties riskProperties;
    @Mock private PositionRepository positionRepository;
    @Mock private PortfolioSnapshotRepository portfolioSnapshotRepository;

    private RiskManager riskManager;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 20);

    @BeforeEach
    void setUp() {
        riskManager = new RiskManager(riskProperties, positionRepository, portfolioSnapshotRepository);
    }

    // ---- Helper methods ----

    private TradeSignal buySignal(String symbol, BigDecimal price, BigDecimal stopLoss, BigDecimal target) {
        return new TradeSignal(
                symbol, TradeSide.BUY, price, stopLoss, target,
                SignalStrength.BUY, 70, "MOMENTUM_BREAKOUT", "test", null, TEST_DATE
        );
    }

    private TradeSignal defaultBuySignal() {
        return buySignal("ZENITHBANK", new BigDecimal("35.00"),
                new BigDecimal("32.00"), new BigDecimal("42.00"));
    }

    private Position openPosition(String symbol, String sector, int quantity, BigDecimal entryPrice) {
        return Position.builder()
                .symbol(symbol)
                .sector(sector)
                .quantity(quantity)
                .avgEntryPrice(entryPrice)
                .currentPrice(entryPrice)
                .isOpen(true)
                .entryDate(TEST_DATE.minusDays(5))
                .build();
    }

    private PortfolioSnapshot snapshot(BigDecimal totalValue, BigDecimal cashBalance) {
        return PortfolioSnapshot.builder()
                .snapshotDate(TEST_DATE)
                .totalValue(totalValue)
                .cashBalance(cashBalance)
                .equityValue(totalValue.subtract(cashBalance))
                .dailyPnl(BigDecimal.ZERO)
                .dailyPnlPct(BigDecimal.ZERO)
                .weeklyPnl(BigDecimal.ZERO)
                .build();
    }

    // ---- Max positions check ----

    @Test
    @DisplayName("Max positions check passes when open positions under limit")
    void maxPositionsCheckPassesWhenUnderLimit() {
        when(riskProperties.getMaxOpenPositions()).thenReturn(10);
        when(positionRepository.countOpenPositions()).thenReturn(7L);

        RiskCheckResult result = riskManager.checkMaxPositions();

        assertThat(result.passed()).isTrue();
        assertThat(result.checkName()).containsIgnoringCase("position");
    }

    @Test
    @DisplayName("Max positions check fails when at limit (10 open positions)")
    void maxPositionsCheckFailsWhenAtLimit() {
        when(riskProperties.getMaxOpenPositions()).thenReturn(10);
        when(positionRepository.countOpenPositions()).thenReturn(10L);

        RiskCheckResult result = riskManager.checkMaxPositions();

        assertThat(result.passed()).isFalse();
        assertThat(result.checkName()).containsIgnoringCase("position");
        assertThat(result.violations()).isNotEmpty();
    }

    // ---- Single position size check ----

    @Test
    @DisplayName("Single position size check passes when trade < 15% of portfolio")
    void singlePositionSizeCheckPasses() {
        // Portfolio total = 1,000,000. Trade value = 3,500 = 0.35% < 15%
        BigDecimal tradeValue = new BigDecimal("3500");
        BigDecimal portfolioValue = new BigDecimal("1000000");
        when(riskProperties.getMaxSinglePositionPct()).thenReturn(new BigDecimal("15.0"));

        RiskCheckResult result = riskManager.checkSinglePositionSize(tradeValue, portfolioValue);

        assertThat(result.passed()).isTrue();
        assertThat(result.checkName()).containsIgnoringCase("position");
    }

    @Test
    @DisplayName("Single position size check fails when trade > 15% of portfolio")
    void singlePositionSizeCheckFails() {
        // Portfolio total = 100,000. Trade value = 20,000 = 20% > 15%
        BigDecimal tradeValue = new BigDecimal("20000");
        BigDecimal portfolioValue = new BigDecimal("100000");
        when(riskProperties.getMaxSinglePositionPct()).thenReturn(new BigDecimal("15.0"));

        RiskCheckResult result = riskManager.checkSinglePositionSize(tradeValue, portfolioValue);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
    }

    // ---- Sector exposure check ----

    @Test
    @DisplayName("Sector exposure check passes when sector below 40%")
    void sectorExposureCheckPasses() {
        // Portfolio = 1,000,000; Banking sector exposure = 200,000 = 20% < 40%
        when(riskProperties.getMaxSectorExposurePct()).thenReturn(new BigDecimal("40.0"));

        RiskCheckResult result = riskManager.checkSectorExposure(
                "Banking", new BigDecimal("200000"), new BigDecimal("1000000"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("Sector exposure check fails when sector exceeds 40%")
    void sectorExposureCheckFails() {
        // Portfolio = 100,000; Banking sector exposure = 42,000 = 42% > 40%
        when(riskProperties.getMaxSectorExposurePct()).thenReturn(new BigDecimal("40.0"));

        RiskCheckResult result = riskManager.checkSectorExposure(
                "Banking", new BigDecimal("42000"), new BigDecimal("100000"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
    }

    // ---- Cash reserve check ----

    @Test
    @DisplayName("Cash reserve check passes with enough cash reserve")
    void cashReserveCheckPasses() {
        // Portfolio = 1,000,000; Cash = 300,000 = 30% > 20% min
        when(riskProperties.getMinCashReservePct()).thenReturn(new BigDecimal("20.0"));

        RiskCheckResult result = riskManager.checkCashReserve(
                new BigDecimal("300000"), new BigDecimal("1000000"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("Cash reserve check fails when below 20% reserve")
    void cashReserveCheckFails() {
        // Portfolio = 100,000; Cash = 15,000 = 15% < 20%
        when(riskProperties.getMinCashReservePct()).thenReturn(new BigDecimal("20.0"));

        RiskCheckResult result = riskManager.checkCashReserve(
                new BigDecimal("15000"), new BigDecimal("100000"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
    }

    // ---- Risk per trade check ----

    @Test
    @DisplayName("Risk per trade check passes when risk < 2%")
    void riskPerTradeCheckPasses() {
        // entry=35, stop=32, qty=100, portfolio=1,000,000
        // risk = (35-32) * 100 = 300 => 300/1,000,000 * 100 = 0.03% < 2%
        when(riskProperties.getMaxRiskPerTradePct()).thenReturn(new BigDecimal("2.0"));

        RiskCheckResult result = riskManager.checkRiskPerTrade(
                new BigDecimal("35.00"), new BigDecimal("32.00"), 100, new BigDecimal("1000000"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("Risk per trade check fails when risk > 2%")
    void riskPerTradeCheckFails() {
        // entry=35, stop=10, qty=100, portfolio=50,000
        // risk = (35-10) * 100 = 2,500 => 2,500/50,000 * 100 = 5.0% > 2%
        when(riskProperties.getMaxRiskPerTradePct()).thenReturn(new BigDecimal("2.0"));

        RiskCheckResult result = riskManager.checkRiskPerTrade(
                new BigDecimal("35.00"), new BigDecimal("10.00"), 100, new BigDecimal("50000"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
    }

    // ---- CORE pool relaxed position limit ----

    @Test
    @DisplayName("CORE pool has relaxed single position limit (20% instead of 15%)")
    void corePoolHasRelaxedPositionLimit() {
        // Position is 18% of portfolio -- would fail SATELLITE (15%) but pass CORE (20%)
        BigDecimal tradeValue = new BigDecimal("18000");
        BigDecimal portfolioValue = new BigDecimal("100000");

        // SATELLITE limit is 15% -- 18% should fail
        RiskCheckResult satelliteResult = riskManager.checkSinglePositionSize(
                tradeValue, portfolioValue, new BigDecimal("15.0"));
        assertThat(satelliteResult.passed()).isFalse();

        // CORE limit is 20% -- 18% should pass
        RiskCheckResult coreResult = riskManager.checkSinglePositionSize(
                tradeValue, portfolioValue, new BigDecimal("20.0"));
        assertThat(coreResult.passed()).isTrue();
    }
}
