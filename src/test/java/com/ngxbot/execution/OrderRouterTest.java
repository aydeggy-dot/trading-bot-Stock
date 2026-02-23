package com.ngxbot.execution;

import com.ngxbot.common.model.TradeSide;
import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.execution.service.*;
import com.ngxbot.notification.service.MessageFormatter;
import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.notification.service.TradeApprovalService;
import com.ngxbot.risk.service.CircuitBreaker;
import com.ngxbot.risk.service.RiskManager;
import com.ngxbot.risk.service.SettlementCashTracker;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRouterTest {

    @Mock private KillSwitchService killSwitchService;
    @Mock private RiskManager riskManager;
    @Mock private CircuitBreaker circuitBreaker;
    @Mock private SettlementCashTracker settlementCashTracker;
    @Mock private TradeApprovalService tradeApprovalService;
    @Mock private NotificationRouter notificationRouter;
    @Mock private MessageFormatter messageFormatter;
    @Mock private TradeOrderRepository tradeOrderRepository;
    @Mock private OrderRecoveryService orderRecoveryService;
    @Mock private BrokerGateway brokerGateway;

    private OrderRouter orderRouter;

    @BeforeEach
    void setUp() {
        orderRouter = new OrderRouter(killSwitchService, riskManager, circuitBreaker,
                settlementCashTracker, tradeApprovalService, notificationRouter,
                messageFormatter, tradeOrderRepository, orderRecoveryService, brokerGateway);
    }

    private TradeSignal buySignal(String symbol) {
        return new TradeSignal(symbol, TradeSide.BUY, new BigDecimal("35.50"),
                new BigDecimal("32.66"), new BigDecimal("41.18"),
                SignalStrength.BUY, 75, "MomentumBreakout", "Test signal",
                null, LocalDate.now());
    }

    @Test
    @DisplayName("routeOrder throws KillSwitchActiveException when kill switch is active")
    void routeOrder_throwsWhenKillSwitchActive() {
        doThrow(new com.ngxbot.common.exception.KillSwitchActiveException("Test halt"))
                .when(killSwitchService).checkOrThrow();

        assertThatThrownBy(() ->
                orderRouter.routeOrder(buySignal("ZENITHBANK"), StrategyPool.SATELLITE, StrategyMarket.NGX))
                .isInstanceOf(com.ngxbot.common.exception.KillSwitchActiveException.class);
    }

    @Test
    @DisplayName("OtpHandler tracks pending OTP state correctly")
    void otpHandler_tracksPendingState() {
        OtpHandler otpHandler = new OtpHandler(notificationRouter, null);

        assertThat(otpHandler.hasPendingOtp()).isFalse();
        assertThat(otpHandler.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("KillSwitchService activate and deactivate work correctly")
    void killSwitch_activateDeactivate() {
        KillSwitchService realKillSwitch = new KillSwitchService(notificationRouter);

        assertThat(realKillSwitch.isActive()).isFalse();

        realKillSwitch.activate("Test reason");
        assertThat(realKillSwitch.isActive()).isTrue();
        assertThat(realKillSwitch.getReason()).isEqualTo("Test reason");
        assertThat(realKillSwitch.getActivatedAt()).isNotNull();

        realKillSwitch.deactivate();
        assertThat(realKillSwitch.isActive()).isFalse();
    }

    @Test
    @DisplayName("KillSwitchService checkOrThrow throws when active")
    void killSwitch_checkOrThrowWhenActive() {
        KillSwitchService realKillSwitch = new KillSwitchService(notificationRouter);
        realKillSwitch.activate("Test halt");

        assertThatThrownBy(realKillSwitch::checkOrThrow)
                .isInstanceOf(com.ngxbot.common.exception.KillSwitchActiveException.class)
                .hasMessageContaining("Test halt");
    }

    @Test
    @DisplayName("BrowserSessionLock serializes operations correctly")
    void browserSessionLock_serializes() throws Exception {
        BrowserSessionLock lock = new BrowserSessionLock();

        assertThat(lock.isLocked()).isFalse();

        String result = lock.executeWithLock(() -> {
            assertThat(lock.isLocked()).isTrue();
            return "executed";
        });

        assertThat(result).isEqualTo("executed");
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    @DisplayName("OrderRecoveryService marks order as UNCERTAIN and activates kill switch")
    void orderRecovery_marksUncertain() {
        OrderRecoveryService realRecovery = new OrderRecoveryService(
                tradeOrderRepository, killSwitchService, notificationRouter);

        TradeOrder order = TradeOrder.builder()
                .orderId("TEST-001")
                .symbol("ZENITHBANK")
                .side("BUY")
                .quantity(100)
                .intendedPrice(new BigDecimal("35.50"))
                .status("SUBMITTED")
                .build();

        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        TradeOrder result = realRecovery.handleExecutionFailure(order, new RuntimeException("Browser crashed"));

        assertThat(result.getStatus()).isEqualTo("UNCERTAIN");
        assertThat(result.getErrorMessage()).contains("Browser crashed");
        verify(killSwitchService).activate(any(String.class));
        verify(notificationRouter).sendUrgent(any(String.class));
    }
}
