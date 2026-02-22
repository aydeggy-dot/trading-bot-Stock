package com.ngxbot.backtest;

import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.service.SimulatedOrderExecutor;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.common.model.TradeSide;
import com.ngxbot.signal.model.TradeSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedOrderExecutorTest {

    @Mock private OhlcvRepository ohlcvRepository;

    private SimulatedOrderExecutor executor;

    private static final LocalDate SIGNAL_DATE = LocalDate.of(2025, 10, 15);
    private static final LocalDate FILL_DATE = LocalDate.of(2025, 10, 16);

    @BeforeEach
    void setUp() {
        executor = new SimulatedOrderExecutor(ohlcvRepository);
    }

    private TradeSignal buySignal(String symbol, BigDecimal price) {
        return new TradeSignal(symbol, TradeSide.BUY, price,
                price.multiply(new BigDecimal("0.92")),
                price.multiply(new BigDecimal("1.16")),
                SignalStrength.BUY, 70, "TestStrategy", "Test signal",
                null, SIGNAL_DATE);
    }

    private OhlcvBar ohlcvBar(String symbol, LocalDate date, BigDecimal open,
                               BigDecimal high, BigDecimal low, BigDecimal close) {
        return OhlcvBar.builder()
                .symbol(symbol)
                .tradeDate(date)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(100000L)
                .build();
    }

    @Test
    @DisplayName("fillBuyOrder fills at next day open + 0.1% slippage for NGX")
    void fillBuyOrder_fillsAtOpenPlusSlippage() {
        BigDecimal openPrice = new BigDecimal("35.00");
        OhlcvBar nextDay = ohlcvBar("ZENITHBANK", FILL_DATE, openPrice,
                new BigDecimal("36.00"), new BigDecimal("34.50"), new BigDecimal("35.50"));

        when(ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                eq("ZENITHBANK"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(nextDay));

        TradeSignal signal = buySignal("ZENITHBANK", new BigDecimal("35.00"));
        BacktestTrade trade = executor.fillBuyOrder(signal, SIGNAL_DATE, "NGX",
                new BigDecimal("500000"), 1L);

        assertThat(trade).isNotNull();
        // Open 35.00 + 0.1% slippage = 35.035
        assertThat(trade.getEntryPrice()).isEqualByComparingTo(new BigDecimal("35.035"));
        assertThat(trade.getSymbol()).isEqualTo("ZENITHBANK");
        assertThat(trade.getSide()).isEqualTo("BUY");
        assertThat(trade.getIsOpen()).isTrue();
        assertThat(trade.getEntryDate()).isEqualTo(FILL_DATE);
    }

    @Test
    @DisplayName("fillBuyOrder returns null when no next-day data exists")
    void fillBuyOrder_returnsNullWhenNoData() {
        when(ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                eq("ZENITHBANK"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        TradeSignal signal = buySignal("ZENITHBANK", new BigDecimal("35.00"));
        BacktestTrade trade = executor.fillBuyOrder(signal, SIGNAL_DATE, "NGX",
                new BigDecimal("500000"), 1L);

        assertThat(trade).isNull();
    }

    @Test
    @DisplayName("fillBuyOrder applies NGX commission rate of 0.15%")
    void fillBuyOrder_appliesNgxCommission() {
        OhlcvBar nextDay = ohlcvBar("ZENITHBANK", FILL_DATE, new BigDecimal("100.00"),
                new BigDecimal("105.00"), new BigDecimal("99.00"), new BigDecimal("103.00"));

        when(ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                eq("ZENITHBANK"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(nextDay));

        TradeSignal signal = buySignal("ZENITHBANK", new BigDecimal("100.00"));
        BacktestTrade trade = executor.fillBuyOrder(signal, SIGNAL_DATE, "NGX",
                new BigDecimal("500000"), 1L);

        assertThat(trade).isNotNull();
        assertThat(trade.getCommission()).isGreaterThan(BigDecimal.ZERO);
        // Commission should be ~0.15% of trade value
        BigDecimal tradeValue = trade.getEntryPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
        BigDecimal expectedCommission = tradeValue.multiply(new BigDecimal("0.0015"));
        // Allow small rounding difference
        assertThat(trade.getCommission().subtract(expectedCommission).abs())
                .isLessThan(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("closeTrade calculates correct net P&L including commission")
    void closeTrade_calculatesNetPnl() {
        BacktestTrade openTrade = BacktestTrade.builder()
                .backtestRunId(1L)
                .symbol("ZENITHBANK")
                .side("BUY")
                .entryDate(SIGNAL_DATE)
                .entryPrice(new BigDecimal("35.00"))
                .quantity(100)
                .commission(new BigDecimal("5.25"))  // entry commission
                .isOpen(true)
                .build();

        BacktestTrade closed = executor.closeTrade(openTrade,
                new BigDecimal("40.00"), FILL_DATE, "NGX", "TARGET_HIT");

        assertThat(closed.getIsOpen()).isFalse();
        assertThat(closed.getExitPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(closed.getExitReason()).isEqualTo("TARGET_HIT");
        // Gross PnL = (40 - 35) * 100 = 500
        assertThat(closed.getGrossPnl()).isEqualByComparingTo(new BigDecimal("500.00"));
        // Net PnL = 500 - exit commission (40*100*0.0015 = 6.00)
        assertThat(closed.getNetPnl()).isLessThan(closed.getGrossPnl());
        assertThat(closed.getHoldingDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("checkStopOrTarget returns STOP_LOSS when low breaches 8% below entry")
    void checkStopOrTarget_detectsStopLoss() {
        BacktestTrade trade = BacktestTrade.builder()
                .entryPrice(new BigDecimal("100.00"))
                .build();

        // Low = 91.00, stop should be at 92.00 (8% below entry)
        OhlcvBar bar = ohlcvBar("TEST", FILL_DATE, new BigDecimal("95.00"),
                new BigDecimal("96.00"), new BigDecimal("91.00"), new BigDecimal("93.00"));

        String result = executor.checkStopOrTarget(trade, bar);

        assertThat(result).isEqualTo("STOP_LOSS");
    }

    @Test
    @DisplayName("checkStopOrTarget returns TARGET_HIT when high reaches 16% above entry")
    void checkStopOrTarget_detectsTargetHit() {
        BacktestTrade trade = BacktestTrade.builder()
                .entryPrice(new BigDecimal("100.00"))
                .build();

        // High = 117.00, target at 116.00 (16% above entry)
        OhlcvBar bar = ohlcvBar("TEST", FILL_DATE, new BigDecimal("110.00"),
                new BigDecimal("117.00"), new BigDecimal("108.00"), new BigDecimal("115.00"));

        String result = executor.checkStopOrTarget(trade, bar);

        assertThat(result).isEqualTo("TARGET_HIT");
    }

    @Test
    @DisplayName("checkStopOrTarget returns null when price stays within range")
    void checkStopOrTarget_returnsNullWhenWithinRange() {
        BacktestTrade trade = BacktestTrade.builder()
                .entryPrice(new BigDecimal("100.00"))
                .build();

        // Low=95, High=110 — stop at 92, target at 116 — neither hit
        OhlcvBar bar = ohlcvBar("TEST", FILL_DATE, new BigDecimal("100.00"),
                new BigDecimal("110.00"), new BigDecimal("95.00"), new BigDecimal("105.00"));

        String result = executor.checkStopOrTarget(trade, bar);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("fillBuyOrder respects available cash and doesn't over-allocate")
    void fillBuyOrder_respectsAvailableCash() {
        OhlcvBar nextDay = ohlcvBar("ZENITHBANK", FILL_DATE, new BigDecimal("100.00"),
                new BigDecimal("105.00"), new BigDecimal("98.00"), new BigDecimal("102.00"));

        when(ohlcvRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
                eq("ZENITHBANK"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(nextDay));

        TradeSignal signal = buySignal("ZENITHBANK", new BigDecimal("100.00"));
        // Small cash amount
        BacktestTrade trade = executor.fillBuyOrder(signal, SIGNAL_DATE, "NGX",
                new BigDecimal("5000"), 1L);

        if (trade != null) {
            BigDecimal totalCost = trade.getEntryPrice().multiply(BigDecimal.valueOf(trade.getQuantity()))
                    .add(trade.getCommission());
            // Total cost should not exceed available cash
            assertThat(totalCost).isLessThanOrEqualTo(new BigDecimal("5000"));
        }
    }
}
