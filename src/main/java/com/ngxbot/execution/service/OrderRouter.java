package com.ngxbot.execution.service;

import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.notification.service.MessageFormatter;
import com.ngxbot.notification.service.NotificationRouter;
import com.ngxbot.notification.service.TradeApprovalService;
import com.ngxbot.risk.service.CircuitBreaker;
import com.ngxbot.risk.service.RiskManager;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.StrategyMarket;
import com.ngxbot.strategy.StrategyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Dispatches trade signals through risk checks, approval flow, and execution.
 * Kill switch checks at every decision point. Screenshots at every step.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderRouter {

    private static final ZoneId WAT = ZoneId.of("Africa/Lagos");
    private static final LocalTime NGX_OPEN = LocalTime.of(10, 0);
    private static final LocalTime NGX_CLOSE = LocalTime.of(14, 30);
    private static final LocalTime US_OPEN = LocalTime.of(14, 30);  // 9:30 AM ET = 14:30 WAT
    private static final LocalTime US_CLOSE = LocalTime.of(21, 0);  // 4:00 PM ET = 21:00 WAT

    private final KillSwitchService killSwitchService;
    private final RiskManager riskManager;
    private final CircuitBreaker circuitBreaker;
    private final SettlementCashTracker settlementCashTracker;
    private final TradeApprovalService tradeApprovalService;
    private final NotificationRouter notificationRouter;
    private final MessageFormatter messageFormatter;
    private final TradeOrderRepository tradeOrderRepository;
    private final OrderRecoveryService orderRecoveryService;

    // BrokerGateway is optional — may not be available if meritrade.enabled=false
    private final BrokerGateway brokerGateway;

    /**
     * Routes a trade signal through the full pipeline:
     * 1. Kill switch check
     * 2. Market hours check
     * 3. Circuit breaker check
     * 4. Risk validation
     * 5. Approval flow (DCA auto-approved, SATELLITE needs WhatsApp approval)
     * 6. Execution via broker
     * 7. Settlement tracking
     */
    public TradeOrder routeOrder(TradeSignal signal, StrategyPool pool, StrategyMarket market) {
        String orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[ORDER] Routing {} {} {} @ {} (pool={}, market={})",
                orderId, signal.side(), signal.symbol(), signal.suggestedPrice(), pool, market);

        // 1. Kill switch check
        killSwitchService.checkOrThrow();

        // 2. Market hours check
        if (!isWithinMarketHours(market)) {
            log.warn("[ORDER] {} rejected: outside market hours for {}", orderId, market);
            return createRejectedOrder(signal, orderId, "Outside market hours for " + market);
        }

        // 3. Circuit breaker check (only blocks SATELLITE)
        if (circuitBreaker.isCircuitBroken(pool)) {
            log.warn("[ORDER] {} rejected: circuit breaker active for {} pool", orderId, pool);
            return createRejectedOrder(signal, orderId, "Circuit breaker active for " + pool);
        }

        // 4. Risk validation
        killSwitchService.checkOrThrow(); // re-check
        if (!riskManager.isTradeAllowed(signal, pool, market)) {
            log.warn("[ORDER] {} rejected: risk checks failed", orderId);
            return createRejectedOrder(signal, orderId, "Risk checks failed");
        }

        // 5. Create order record
        TradeOrder order = TradeOrder.builder()
                .orderId(orderId)
                .symbol(signal.symbol())
                .side(signal.side().name())
                .quantity(calculateQuantity(signal, market))
                .intendedPrice(signal.suggestedPrice())
                .strategy(signal.strategy())
                .confidenceScore(signal.confidenceScore())
                .status("PENDING_APPROVAL")
                .build();
        order = tradeOrderRepository.save(order);

        // 6. Approval flow
        killSwitchService.checkOrThrow(); // re-check
        boolean approved = requestApproval(signal, pool, market, order);
        if (!approved) {
            order.setStatus("REJECTED");
            return tradeOrderRepository.save(order);
        }

        order.setStatus("APPROVED");
        order = tradeOrderRepository.save(order);

        // 7. Execute via broker
        killSwitchService.checkOrThrow(); // re-check
        try {
            return executeOrder(order, signal, market);
        } catch (Exception e) {
            return orderRecoveryService.handleExecutionFailure(order, e);
        }
    }

    private boolean requestApproval(TradeSignal signal, StrategyPool pool,
                                     StrategyMarket market, TradeOrder order) {
        // DCA and CORE trades are auto-approved
        if (pool == StrategyPool.CORE || tradeApprovalService.isDcaAutoApproved()) {
            log.info("[ORDER] {} auto-approved (pool={})", order.getOrderId(), pool);
            return true;
        }

        // SATELLITE trades require WhatsApp approval
        String currency = market == StrategyMarket.US ? "USD" : "NGN";
        return tradeApprovalService.requestApproval(
                order.getOrderId(),
                signal.side().name(),
                signal.symbol(),
                order.getQuantity(),
                signal.suggestedPrice(),
                currency,
                BigDecimal.ZERO // risk pct placeholder
        );
    }

    private TradeOrder executeOrder(TradeOrder order, TradeSignal signal, StrategyMarket market) throws Exception {
        log.info("[ORDER] Executing {} via broker", order.getOrderId());
        order.setStatus("SUBMITTED");
        order = tradeOrderRepository.save(order);

        String brokerOrderId = brokerGateway.submitOrder(order);
        order.setOrderId(brokerOrderId);
        order.setStatus("CONFIRMED");
        order.setExecutedPrice(signal.suggestedPrice());
        order.setExecutedAt(java.time.LocalDateTime.now());

        // Track settlement
        BigDecimal tradeValue = signal.suggestedPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()));
        if ("SELL".equals(order.getSide())) {
            settlementCashTracker.recordSale(market, tradeValue, LocalDate.now());
        } else {
            settlementCashTracker.recordPurchase(market, tradeValue);
        }

        // Notify
        String currency = market == StrategyMarket.US ? "USD" : "NGN";
        notificationRouter.sendAlert(messageFormatter.formatTradeAlert(
                order.getSide(), order.getSymbol(), order.getQuantity(),
                signal.suggestedPrice(), currency, BigDecimal.ZERO));

        order = tradeOrderRepository.save(order);
        log.info("[ORDER] {} executed successfully: {} {} x {} @ {}",
                order.getOrderId(), order.getSide(), order.getSymbol(),
                order.getQuantity(), order.getExecutedPrice());
        return order;
    }

    private int calculateQuantity(TradeSignal signal, StrategyMarket market) {
        // Use available cash and signal price to determine position size
        BigDecimal availableCash = settlementCashTracker.getAvailableCash(market);
        BigDecimal maxPositionSize = availableCash.multiply(new BigDecimal("0.15"));
        if (signal.suggestedPrice().compareTo(BigDecimal.ZERO) <= 0) return 0;
        return maxPositionSize.divide(signal.suggestedPrice(), 0, java.math.RoundingMode.DOWN).intValue();
    }

    boolean isWithinMarketHours(StrategyMarket market) {
        LocalTime now = ZonedDateTime.now(WAT).toLocalTime();
        if (market == StrategyMarket.NGX) {
            return !now.isBefore(NGX_OPEN) && !now.isAfter(NGX_CLOSE);
        } else if (market == StrategyMarket.US) {
            return !now.isBefore(US_OPEN) && !now.isAfter(US_CLOSE);
        }
        // BOTH: check if either market is open
        return (!now.isBefore(NGX_OPEN) && !now.isAfter(NGX_CLOSE)) ||
               (!now.isBefore(US_OPEN) && !now.isAfter(US_CLOSE));
    }

    private TradeOrder createRejectedOrder(TradeSignal signal, String orderId, String reason) {
        TradeOrder order = TradeOrder.builder()
                .orderId(orderId)
                .symbol(signal.symbol())
                .side(signal.side().name())
                .quantity(0)
                .intendedPrice(signal.suggestedPrice())
                .strategy(signal.strategy())
                .status("REJECTED")
                .errorMessage(reason)
                .build();
        return tradeOrderRepository.save(order);
    }
}
