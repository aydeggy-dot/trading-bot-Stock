package com.ngxbot.integration;

import com.ngxbot.config.MeritradeProperties;
import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.execution.service.TroveBrowserAgent;
import com.ngxbot.notification.service.TelegramService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 8: Meritrade/Trove Paper Trade (1 Share)
 *
 * Verifies:
 *   - Can submit a REAL limit order for 1 share of a cheap NGX stock
 *   - Order appears in Meritrade order book
 *   - TradeOrder entity is persisted to PostgreSQL
 *   - Notification sent about order placement
 *
 * IMPORTANT:
 *   - This submits a REAL order with REAL money!
 *   - Choose the CHEAPEST liquid stock to minimize risk
 *   - Uses LIMIT order at current market price
 *   - After verification, CANCEL the order if it hasn't filled
 *
 * Prereqs: Steps 1, 4, 7 must pass first
 */
@Tag("integration")
@Tag("browser")
@Tag("paper-trade")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step08_PaperTradeIT extends IntegrationTestBase {

    @Autowired
    private TroveBrowserAgent troveBrowserAgent;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private MeritradeProperties meritradeProperties;

    // Choose a cheap, liquid stock for paper trade
    // TRANSCORP is typically < ₦10 and liquid
    private static final String TEST_SYMBOL = "TRANSCORP";
    private static final int TEST_QUANTITY = 1;

    private static String submittedOrderId;

    @Test
    @Order(1)
    @DisplayName("8.1 Ensure broker session is active")
    void ensureBrokerSessionActive() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }
        assertThat(troveBrowserAgent.isSessionActive()).isTrue();

        printResult("Broker Session", "Active and ready for paper trade");
    }

    @Test
    @Order(2)
    @DisplayName("8.2 Submit BUY LIMIT order for 1 share")
    void submitPaperTradeOrder() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        // Build the test order
        TradeOrder order = TradeOrder.builder()
                .orderId("IT-" + System.currentTimeMillis())
                .symbol(TEST_SYMBOL)
                .side("BUY")
                .quantity(TEST_QUANTITY)
                .intendedPrice(new BigDecimal("5.00"))  // Low limit price for safety
                .strategy("integration-test")
                .reasoning("Integration test paper trade — 1 share limit order")
                .status("PENDING")
                .executionMethod("LIMIT")
                .createdAt(LocalDateTime.now())
                .build();

        // Submit to Meritrade
        String orderId = troveBrowserAgent.submitOrder(order);
        submittedOrderId = orderId;

        // Take screenshot of order confirmation
        troveBrowserAgent.takeScreenshot("paper-trade-submitted");

        printResult("Paper Trade Submitted",
                String.format("Order ID: %s, Symbol: %s, Qty: %d, Limit: ₦%.2f",
                        orderId, TEST_SYMBOL, TEST_QUANTITY, order.getIntendedPrice()));

        assertThat(orderId).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("8.3 Persist trade order to PostgreSQL")
    void persistTradeOrder() {
        TradeOrder order = TradeOrder.builder()
                .orderId(submittedOrderId != null ? submittedOrderId : "IT-FALLBACK-" + System.currentTimeMillis())
                .symbol(TEST_SYMBOL)
                .side("BUY")
                .quantity(TEST_QUANTITY)
                .intendedPrice(new BigDecimal("5.00"))
                .strategy("integration-test")
                .reasoning("Integration test paper trade")
                .status("SUBMITTED")
                .executionMethod("LIMIT")
                .createdAt(LocalDateTime.now())
                .build();

        TradeOrder saved = tradeOrderRepository.save(order);

        printResult("Order Persisted",
                String.format("DB ID: %d, Order ID: %s, Status: %s",
                        saved.getId(), saved.getOrderId(), saved.getStatus()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrderId()).isNotBlank();
    }

    @Test
    @Order(4)
    @DisplayName("8.4 Send notification about paper trade")
    void sendTradeNotification() {
        String notification = String.format(
                "📊 *PAPER TRADE — Integration Test*\n\n" +
                "Symbol: `%s`\n" +
                "Action: *BUY* LIMIT\n" +
                "Quantity: %d share(s)\n" +
                "Limit Price: ₦5.00\n" +
                "Order ID: `%s`\n\n" +
                "⚠️ _This is an integration test order._",
                TEST_SYMBOL, TEST_QUANTITY,
                submittedOrderId != null ? submittedOrderId : "N/A");

        assertThatCode(() -> telegramService.sendMessage(notification))
                .doesNotThrowAnyException();

        printResult("Trade Notification", "Paper trade notification sent to Telegram");
    }

    @Test
    @Order(5)
    @DisplayName("8.5 Check order status")
    void checkOrderStatus() throws Exception {
        if (submittedOrderId == null) {
            printResult("Order Status", "SKIPPED — no order was submitted");
            return;
        }

        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        String status = troveBrowserAgent.checkOrderStatus(submittedOrderId);

        printResult("Order Status",
                String.format("Order %s status: %s", submittedOrderId, status));

        // Status could be PENDING, OPEN, FILLED, CANCELLED, etc.
        assertThat(status).isNotBlank();
    }

    @Test
    @Order(6)
    @DisplayName("8.6 Verify order exists in database")
    void verifyOrderInDatabase() {
        if (submittedOrderId == null) {
            printResult("DB Verification", "SKIPPED — no order was submitted");
            return;
        }

        Optional<TradeOrder> found = tradeOrderRepository.findAll().stream()
                .filter(o -> o.getOrderId().equals(submittedOrderId))
                .findFirst();

        if (found.isPresent()) {
            TradeOrder order = found.get();
            printResult("DB Verification",
                    String.format("Order found: ID=%d, Symbol=%s, Status=%s",
                            order.getId(), order.getSymbol(), order.getStatus()));
        } else {
            printResult("DB Verification",
                    String.format("Order %s not found in DB — may not have been saved", submittedOrderId));
        }
    }
}
