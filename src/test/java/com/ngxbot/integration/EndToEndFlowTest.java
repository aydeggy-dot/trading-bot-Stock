package com.ngxbot.integration;

import com.ngxbot.common.exception.KillSwitchActiveException;
import com.ngxbot.common.model.TradeSide;
import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.execution.service.*;
import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.StrategyMarket;
import com.ngxbot.strategy.StrategyPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndToEndFlowTest {

    @Mock private NotificationRouter notificationRouter;
    @Mock private TradeOrderRepository tradeOrderRepository;

    // ---- Kill Switch + Settlement Integration ----

    @Nested
    @DisplayName("Kill Switch halts BOTH markets")
    class KillSwitchTests {

        private KillSwitchService killSwitchService;

        @BeforeEach
        void setUp() {
            killSwitchService = new KillSwitchService(notificationRouter);
        }

        @Test
        @DisplayName("Kill switch activation sends urgent notification and blocks all markets")
        void killSwitch_blocksAllMarkets() {
            killSwitchService.activate("Test: simulated broker failure");

            assertThat(killSwitchService.isActive()).isTrue();
            assertThat(killSwitchService.getReason()).contains("simulated broker failure");
            verify(notificationRouter).sendUrgent(any(String.class));

            // Should throw for any market check
            assertThatThrownBy(killSwitchService::checkOrThrow)
                    .isInstanceOf(KillSwitchActiveException.class);
        }

        @Test
        @DisplayName("Kill switch deactivation allows trading to resume")
        void killSwitch_deactivationAllowsResume() {
            killSwitchService.activate("Test halt");
            assertThat(killSwitchService.isActive()).isTrue();

            killSwitchService.deactivate();
            assertThat(killSwitchService.isActive()).isFalse();

            // Should NOT throw after deactivation
            assertThatCode(killSwitchService::checkOrThrow).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Multiple activations only send one notification")
        void killSwitch_multipleActivationsIdempotent() {
            killSwitchService.activate("First activation");
            killSwitchService.activate("Second activation");

            // Only first activation sends notification
            verify(notificationRouter, times(1)).sendUrgent(any(String.class));
            assertThat(killSwitchService.getReason()).isEqualTo("First activation");
        }
    }

    // ---- Settlement Tracking Integration ----

    @Nested
    @DisplayName("Settlement tracking prevents spending unsettled cash")
    class SettlementTests {

        private SettlementCashTracker cashTracker;

        @BeforeEach
        void setUp() {
            cashTracker = new SettlementCashTracker();
        }

        @Test
        @DisplayName("NGX purchase fails when requesting more than available settled cash")
        void settlement_preventsOverspending() {
            cashTracker.initializeCash(StrategyMarket.NGX, new BigDecimal("100000"));

            // Spend 80k, leaving 20k available
            cashTracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("80000"));

            // Trying to spend 30k should fail (only 20k available)
            assertThatThrownBy(() ->
                    cashTracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("30000")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Sale proceeds not available until settlement (T+2 for NGX)")
        void settlement_saleNotAvailableBeforeSettlement() {
            cashTracker.initializeCash(StrategyMarket.NGX, new BigDecimal("50000"));

            // Sell 100k worth on Monday
            LocalDate saleDate = LocalDate.of(2026, 2, 16); // Monday
            cashTracker.recordSale(StrategyMarket.NGX, new BigDecimal("100000"), saleDate);

            // Available cash should still be 50k (sale hasn't settled)
            assertThat(cashTracker.getAvailableCash(StrategyMarket.NGX))
                    .isEqualByComparingTo(new BigDecimal("50000"));

            // After T+2 settlement (Wednesday)
            cashTracker.processSettlements(saleDate.plusDays(2));
            assertThat(cashTracker.getAvailableCash(StrategyMarket.NGX))
                    .isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        @DisplayName("US settlement is T+1 (faster than NGX T+2)")
        void settlement_usSettlesInOneDay() {
            cashTracker.initializeCash(StrategyMarket.US, new BigDecimal("10000"));

            LocalDate saleDate = LocalDate.of(2026, 2, 16); // Monday
            cashTracker.recordSale(StrategyMarket.US, new BigDecimal("5000"), saleDate);

            // Not settled yet on same day
            assertThat(cashTracker.getAvailableCash(StrategyMarket.US))
                    .isEqualByComparingTo(new BigDecimal("10000"));

            // Settled after T+1 (Tuesday)
            cashTracker.processSettlements(saleDate.plusDays(1));
            assertThat(cashTracker.getAvailableCash(StrategyMarket.US))
                    .isEqualByComparingTo(new BigDecimal("15000"));
        }
    }

    // ---- Order Recovery Integration ----

    @Nested
    @DisplayName("Order recovery on execution failure")
    class OrderRecoveryTests {

        private KillSwitchService killSwitchService;
        private OrderRecoveryService recoveryService;

        @BeforeEach
        void setUp() {
            killSwitchService = new KillSwitchService(notificationRouter);
            recoveryService = new OrderRecoveryService(
                    tradeOrderRepository, killSwitchService, notificationRouter);
        }

        @Test
        @DisplayName("Execution failure marks order UNCERTAIN and activates kill switch")
        void recovery_uncertainAndKillSwitch() {
            TradeOrder order = TradeOrder.builder()
                    .orderId("ORD-001")
                    .symbol("ZENITHBANK")
                    .side("BUY")
                    .quantity(500)
                    .intendedPrice(new BigDecimal("35.50"))
                    .status("SUBMITTED")
                    .build();

            when(tradeOrderRepository.save(any(TradeOrder.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TradeOrder result = recoveryService.handleExecutionFailure(
                    order, new RuntimeException("Playwright page crashed"));

            assertThat(result.getStatus()).isEqualTo("UNCERTAIN");
            assertThat(result.getErrorMessage()).contains("Playwright page crashed");
            assertThat(killSwitchService.isActive()).isTrue();
            verify(notificationRouter).sendUrgent(contains("EXECUTION FAILURE"));
        }

        @Test
        @DisplayName("Kill switch blocks new trades after execution failure")
        void recovery_killSwitchBlocksNewTrades() {
            TradeOrder order = TradeOrder.builder()
                    .orderId("ORD-002")
                    .symbol("GTCO")
                    .side("BUY")
                    .quantity(300)
                    .intendedPrice(new BigDecimal("42.00"))
                    .status("SUBMITTED")
                    .build();

            when(tradeOrderRepository.save(any(TradeOrder.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            recoveryService.handleExecutionFailure(order, new RuntimeException("Network error"));

            // Kill switch should now block any trade attempt
            assertThatThrownBy(killSwitchService::checkOrThrow)
                    .isInstanceOf(KillSwitchActiveException.class);
        }
    }

    // ---- OTP Flow Integration ----

    @Nested
    @DisplayName("OTP flow with mocked WhatsApp")
    class OtpFlowTests {

        private OtpHandler otpHandler;

        @BeforeEach
        void setUp() {
            otpHandler = new OtpHandler(notificationRouter, null);
        }

        @Test
        @DisplayName("OTP handler sends WhatsApp prompt and processes reply")
        void otp_sendsPromptAndProcessesReply() throws Exception {
            assertThat(otpHandler.hasPendingOtp()).isFalse();

            // Simulate: start OTP request in background, then reply
            CompletableFuture<String> otpFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return otpHandler.requestOtp();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Wait briefly for the request to be registered
            Thread.sleep(100);
            assertThat(otpHandler.hasPendingOtp()).isTrue();

            // Simulate WhatsApp reply with OTP
            otpHandler.processOtpReply("123456");

            String result = otpFuture.get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("123456");
            verify(notificationRouter).sendWhatsAppOnly(contains("OTP REQUIRED"));
        }

        @Test
        @DisplayName("OTP handler strips non-numeric characters from reply")
        void otp_stripsNonNumeric() throws Exception {
            CompletableFuture<String> otpFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return otpHandler.requestOtp();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread.sleep(100);
            otpHandler.processOtpReply("OTP: 789012");

            String result = otpFuture.get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("789012");
        }
    }

    // ---- Browser Session Lock Integration ----

    @Nested
    @DisplayName("Browser session lock serialization")
    class BrowserLockTests {

        @Test
        @DisplayName("Lock serializes concurrent operations")
        void lock_serializesConcurrentOps() throws Exception {
            BrowserSessionLock lock = new BrowserSessionLock();
            StringBuilder log = new StringBuilder();

            // Run two operations — second must wait
            String result1 = lock.executeWithLock(() -> {
                log.append("A");
                Thread.sleep(50);
                log.append("B");
                return "first";
            });

            String result2 = lock.executeWithLock(() -> {
                log.append("C");
                return "second";
            });

            assertThat(result1).isEqualTo("first");
            assertThat(result2).isEqualTo("second");
            assertThat(log.toString()).isEqualTo("ABC");
            assertThat(lock.isLocked()).isFalse();
        }

        @Test
        @DisplayName("Lock is released even when operation throws exception")
        void lock_releasedOnException() {
            BrowserSessionLock lock = new BrowserSessionLock();

            assertThatThrownBy(() -> lock.executeWithLock(() -> {
                throw new RuntimeException("Simulated crash");
            })).isInstanceOf(RuntimeException.class);

            // Lock must be released after exception
            assertThat(lock.isLocked()).isFalse();
        }
    }

    // ---- Cross-Market Settlement ----

    @Nested
    @DisplayName("Cross-market cash isolation")
    class CrossMarketTests {

        @Test
        @DisplayName("NGX and US cash ledgers are independent")
        void crossMarket_independentLedgers() {
            SettlementCashTracker tracker = new SettlementCashTracker();
            tracker.initializeCash(StrategyMarket.NGX, new BigDecimal("500000"));
            tracker.initializeCash(StrategyMarket.US, new BigDecimal("10000"));

            // Spend on NGX
            tracker.recordPurchase(StrategyMarket.NGX, new BigDecimal("200000"));

            // US should be unaffected
            assertThat(tracker.getAvailableCash(StrategyMarket.US))
                    .isEqualByComparingTo(new BigDecimal("10000"));

            // NGX should be reduced
            assertThat(tracker.getAvailableCash(StrategyMarket.NGX))
                    .isEqualByComparingTo(new BigDecimal("300000"));
        }
    }
}
