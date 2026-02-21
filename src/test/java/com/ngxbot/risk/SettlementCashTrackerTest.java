package com.ngxbot.risk;

import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.strategy.StrategyMarket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCashTrackerTest {

    private SettlementCashTracker tracker;

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 19);    // Monday
    private static final LocalDate TUESDAY = LocalDate.of(2026, 1, 20);   // Tuesday
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 1, 21); // Wednesday
    private static final LocalDate THURSDAY = LocalDate.of(2026, 1, 22);  // Thursday
    private static final LocalDate FRIDAY = LocalDate.of(2026, 1, 23);    // Friday

    @BeforeEach
    void setUp() {
        tracker = new SettlementCashTracker();
    }

    @Test
    @DisplayName("initializeCash sets available cash for market")
    void initializeCashSetsAvailable() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("500000.00"));

        BigDecimal available = tracker.getAvailableCash(StrategyMarket.NGX);

        assertThat(available).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("recordPurchase reduces available cash")
    void recordPurchaseReducesAvailable() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("500000.00"));

        tracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("100000.00"));

        BigDecimal available = tracker.getAvailableCash(StrategyMarket.NGX);
        assertThat(available).isEqualByComparingTo(new BigDecimal("400000.00"));
    }

    @Test
    @DisplayName("recordSale adds to settling cash, not immediately available")
    void recordSaleAddsToSettling() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("500000.00"));

        // Sell 100,000 worth on Monday -- should go to settling, not available
        tracker.recordSale(StrategyMarket.NGX, new BigDecimal("100000.00"), MONDAY);

        // Available should still be 500,000 (sale proceeds are settling)
        BigDecimal available = tracker.getAvailableCash(StrategyMarket.NGX);
        assertThat(available).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("NGX T+2: settling cash becomes available after 2 business days")
    void processSettlementsNgxT2() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("500000.00"));

        // Sell on Monday
        tracker.recordSale(StrategyMarket.NGX, new BigDecimal("100000.00"), MONDAY);

        // Tuesday (T+1): still settling
        tracker.processSettlements(TUESDAY);
        assertThat(tracker.getAvailableCash(StrategyMarket.NGX))
                .isEqualByComparingTo(new BigDecimal("500000.00"));

        // Wednesday (T+2): should be settled and available
        tracker.processSettlements(WEDNESDAY);
        assertThat(tracker.getAvailableCash(StrategyMarket.NGX))
                .isEqualByComparingTo(new BigDecimal("600000.00"));
    }

    @Test
    @DisplayName("US T+1: settling cash becomes available after 1 business day")
    void processSettlementsUsT1() {
        tracker.initializeCash(StrategyMarket.US, new BigDecimal("200000.00"));

        // Sell on Monday
        tracker.recordSale(StrategyMarket.US, new BigDecimal("50000.00"), MONDAY);

        // Tuesday (T+1): should be settled and available
        tracker.processSettlements(TUESDAY);
        assertThat(tracker.getAvailableCash(StrategyMarket.US))
                .isEqualByComparingTo(new BigDecimal("250000.00"));
    }

    @Test
    @DisplayName("Settling cash is NOT included in available cash")
    void cannotSpendSettlingCash() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("100000.00"));

        // Spend 80,000
        tracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("80000.00"));

        // Sell 150,000 worth (proceeds settling)
        tracker.recordSale(StrategyMarket.NGX, new BigDecimal("150000.00"), MONDAY);

        // Available should be 20,000 only (100,000 - 80,000), NOT 170,000
        BigDecimal available = tracker.getAvailableCash(StrategyMarket.NGX);
        assertThat(available).isEqualByComparingTo(new BigDecimal("20000.00"));

        // After settlement (T+2), the 150,000 becomes available
        tracker.processSettlements(WEDNESDAY);
        assertThat(tracker.getAvailableCash(StrategyMarket.NGX))
                .isEqualByComparingTo(new BigDecimal("170000.00"));
    }

    @Test
    @DisplayName("Purchase throws IllegalStateException when insufficient funds")
    void purchaseFailsOnInsufficientFunds() {
        tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("50000.00"));

        // Attempt to spend more than available should throw
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                tracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("80000.00"))
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Insufficient available cash");

        // Available should remain unchanged after failed purchase
        BigDecimal available = tracker.getAvailableCash(StrategyMarket.NGX);
        assertThat(available).isEqualByComparingTo(new BigDecimal("50000.00"));
    }
}
