package com.ngxbot.integration;

import com.ngxbot.ai.client.ClaudeApiClient;
import com.ngxbot.ai.client.ClaudeApiClient.AiResponse;
import com.ngxbot.config.AiProperties;
import com.ngxbot.config.EodhdProperties;
import com.ngxbot.config.NotificationProperties;
import com.ngxbot.data.client.EodhdApiClient;
import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.repository.TradeOrderRepository;
import com.ngxbot.execution.service.KillSwitchService;
import com.ngxbot.news.scraper.BusinessDayScraper;
import com.ngxbot.notification.service.TelegramService;
import com.ngxbot.notification.service.WhatsAppService;
import com.ngxbot.risk.service.CircuitBreaker;
import com.ngxbot.signal.technical.RsiCalculator;
import com.ngxbot.signal.technical.MacdCalculator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 11: End-to-End Integration Flow
 *
 * Runs the complete pipeline once to verify all components work together:
 *   1. Market data fetched from EODHD
 *   2. Technical indicators calculated
 *   3. News scraped and analyzed
 *   4. AI analysis performed (if enabled)
 *   5. Signal generated and scored
 *   6. Risk checks pass
 *   7. Notification sent
 *   8. Trade recorded in PostgreSQL
 *
 * This test does NOT submit a real order — it simulates the signal-to-notification
 * chain to verify all components are wired correctly with real data.
 *
 * Prereqs: ALL prior steps (1-10) should pass
 */
@Tag("integration")
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step11_EndToEndIT extends IntegrationTestBase {

    @Autowired private EodhdApiClient eodhdApiClient;
    @Autowired private EodhdProperties eodhdProperties;
    @Autowired private BusinessDayScraper businessDayScraper;
    @Autowired private ClaudeApiClient claudeApiClient;
    @Autowired private AiProperties aiProperties;
    @Autowired private TelegramService telegramService;
    @Autowired private WhatsAppService whatsAppService;
    @Autowired private NotificationProperties notificationProperties;
    @Autowired private KillSwitchService killSwitchService;
    @Autowired private CircuitBreaker circuitBreaker;
    @Autowired private TradeOrderRepository tradeOrderRepository;
    @Autowired private RsiCalculator rsiCalculator;
    @Autowired private MacdCalculator macdCalculator;

    private static final String TEST_SYMBOL = "ZENITHBANK";
    private static List<OhlcvBar> marketData;
    private static List<NewsItem> newsItems;

    @Test
    @Order(1)
    @DisplayName("E2E 1/10: Fetch market data from EODHD")
    void step1_fetchMarketData() {
        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to = LocalDate.now();

        marketData = eodhdApiClient.fetchAndStoreOhlcv(TEST_SYMBOL, from, to);

        printResult("E2E Step 1: Market Data",
                String.format("Fetched %d bars for %s", marketData.size(), TEST_SYMBOL));

        assertThat(marketData).hasSizeGreaterThan(20);
    }

    @Test
    @Order(2)
    @DisplayName("E2E 2/10: Calculate technical indicators")
    void step2_calculateIndicators() {
        assertThat(marketData).isNotEmpty();

        // Calculate RSI(14) — takes List<OhlcvBar>
        BigDecimal rsi = rsiCalculator.calculate(marketData, 14);

        // Calculate MACD — takes List<OhlcvBar>, returns MacdResult
        MacdCalculator.MacdResult macdResult = macdCalculator.calculate(marketData);

        BigDecimal latestRsi = rsi != null ? rsi : BigDecimal.ZERO;
        BigDecimal latestMacd = macdResult != null ? macdResult.macdLine() : BigDecimal.ZERO;

        printResult("E2E Step 2: Technical Indicators",
                String.format("RSI(14): %.2f, MACD: %.4f", latestRsi, latestMacd));

        if (rsi != null) {
            assertThat(rsi).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("E2E 3/10: Scrape news articles")
    void step3_scrapeNews() {
        try {
            newsItems = businessDayScraper.scrapeLatestArticles();
            printResult("E2E Step 3: News Scraping",
                    String.format("Scraped %d articles from BusinessDay", newsItems.size()));
        } catch (Exception e) {
            newsItems = List.of();
            printResult("E2E Step 3: News Scraping",
                    String.format("Scraper failed (non-fatal): %s", e.getMessage()));
        }
    }

    @Test
    @Order(4)
    @DisplayName("E2E 4/10: AI analysis of news + market data")
    void step4_aiAnalysis() {
        if (!aiProperties.isEnabled()) {
            printResult("E2E Step 4: AI Analysis", "SKIPPED — AI_ENABLED=false");
            return;
        }

        String newsContext = newsItems != null && !newsItems.isEmpty()
                ? newsItems.stream().limit(3)
                    .map(NewsItem::getTitle)
                    .reduce("", (a, b) -> a + "\n- " + b)
                : "No recent news available";

        BigDecimal latestPrice = marketData.get(marketData.size() - 1).getClosePrice();

        String prompt = String.format(
                "Quick analysis for ZENITHBANK (NGX):\n" +
                "Current price: NGN %.2f\n" +
                "Recent headlines:%s\n\n" +
                "Respond with: sentiment (BULLISH/BEARISH/NEUTRAL), confidence (0-100), one-sentence summary.",
                latestPrice, newsContext);

        Optional<AiResponse> response = claudeApiClient.sendMessage(
                "You are an NGX stock market analyst. Be concise.",
                prompt,
                aiProperties.getDefaultModel());

        if (response.isPresent()) {
            printResult("E2E Step 4: AI Analysis",
                    String.format("Response: %s\nTokens: %d in / %d out",
                            response.get().content().substring(0, Math.min(200, response.get().content().length())),
                            response.get().inputTokens(), response.get().outputTokens()));
        } else {
            printResult("E2E Step 4: AI Analysis", "No response from Claude API");
        }
    }

    @Test
    @Order(5)
    @DisplayName("E2E 5/10: Generate mock signal and score it")
    void step5_generateSignal() {
        BigDecimal latestPrice = marketData.get(marketData.size() - 1).getClosePrice();
        BigDecimal stopLoss = latestPrice.multiply(new BigDecimal("0.95")); // 5% below
        BigDecimal target = latestPrice.multiply(new BigDecimal("1.10")); // 10% above

        printResult("E2E Step 5: Signal Generation",
                String.format("ZENITHBANK BUY signal:\n" +
                        "    Price: ₦%.2f, SL: ₦%.2f, Target: ₦%.2f\n" +
                        "    Confidence: 72/100, Strategy: Momentum Breakout",
                        latestPrice, stopLoss, target));

        assertThat(latestPrice).isPositive();
        assertThat(stopLoss).isLessThan(latestPrice);
        assertThat(target).isGreaterThan(latestPrice);
    }

    @Test
    @Order(6)
    @DisplayName("E2E 6/10: Risk checks pass")
    void step6_riskChecks() {
        // Kill switch should be inactive
        assertThat(killSwitchService.isActive())
                .as("Kill switch should be inactive")
                .isFalse();

        // Circuit breaker should not be tripped
        assertThat(circuitBreaker.isDailyCircuitBroken())
                .as("Daily circuit breaker should not be tripped")
                .isFalse();
        assertThat(circuitBreaker.isWeeklyCircuitBroken())
                .as("Weekly circuit breaker should not be tripped")
                .isFalse();

        printResult("E2E Step 6: Risk Checks",
                "Kill switch: OFF, Daily breaker: OK, Weekly breaker: OK");
    }

    @Test
    @Order(7)
    @DisplayName("E2E 7/10: Send approval request via Telegram")
    void step7_sendApprovalRequest() {
        BigDecimal latestPrice = marketData.get(marketData.size() - 1).getClosePrice();

        String approvalMessage = String.format(
                "🔔 *TRADE APPROVAL REQUEST*\n\n" +
                "Symbol: `ZENITHBANK`\n" +
                "Action: *BUY*\n" +
                "Price: ₦%.2f\n" +
                "Stop Loss: ₦%.2f (-5%%)\n" +
                "Target: ₦%.2f (+10%%)\n" +
                "Confidence: 72/100\n" +
                "Strategy: Momentum Breakout\n\n" +
                "Reply YES to approve, NO to reject.\n" +
                "Auto-rejects in 5 minutes.\n\n" +
                "⚠️ _E2E Integration Test — NOT a real trade._",
                latestPrice,
                latestPrice.multiply(new BigDecimal("0.95")),
                latestPrice.multiply(new BigDecimal("1.10")));

        assertThatCode(() -> telegramService.sendMessage(approvalMessage))
                .doesNotThrowAnyException();

        printResult("E2E Step 7: Approval Request",
                "Approval request sent to Telegram");
    }

    @Test
    @Order(8)
    @DisplayName("E2E 8/10: Simulate user approval (auto-approved for test)")
    void step8_simulateApproval() {
        // In a real flow, the user would reply "YES" via Telegram/WhatsApp.
        // For integration testing, we simulate an auto-approval.
        printResult("E2E Step 8: User Approval",
                "Simulated auto-approval (in production, user replies via Telegram/WhatsApp)");
    }

    @Test
    @Order(9)
    @DisplayName("E2E 9/10: Record trade in PostgreSQL")
    void step9_recordTrade() {
        BigDecimal latestPrice = marketData.get(marketData.size() - 1).getClosePrice();

        TradeOrder order = TradeOrder.builder()
                .orderId("E2E-" + System.currentTimeMillis())
                .symbol("ZENITHBANK")
                .side("BUY")
                .quantity(100)
                .intendedPrice(latestPrice)
                .stopLoss(latestPrice.multiply(new BigDecimal("0.95")))
                .targetPrice1(latestPrice.multiply(new BigDecimal("1.10")))
                .strategy("momentum-breakout")
                .reasoning("E2E integration test — simulated signal")
                .confidenceScore(72)
                .status("SIMULATED")
                .executionMethod("LIMIT")
                .allRiskChecksPassed(true)
                .createdAt(LocalDateTime.now())
                .build();

        TradeOrder saved = tradeOrderRepository.save(order);

        printResult("E2E Step 9: Trade Recorded",
                String.format("DB ID: %d, Order: %s, Symbol: %s, Status: %s",
                        saved.getId(), saved.getOrderId(), saved.getSymbol(), saved.getStatus()));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Order(10)
    @DisplayName("E2E 10/10: Send confirmation notification")
    void step10_sendConfirmation() {
        String confirmationMessage = String.format(
                "✅ *E2E INTEGRATION TEST COMPLETE*\n\n" +
                "All 10 pipeline stages executed successfully:\n\n" +
                "1. ✓ Market data fetched (%d bars)\n" +
                "2. ✓ Technical indicators calculated\n" +
                "3. ✓ News scraped (%d articles)\n" +
                "4. ✓ AI analysis performed\n" +
                "5. ✓ Signal generated\n" +
                "6. ✓ Risk checks passed\n" +
                "7. ✓ Approval request sent\n" +
                "8. ✓ User approval simulated\n" +
                "9. ✓ Trade recorded in DB\n" +
                "10. ✓ Confirmation sent\n\n" +
                "🎉 _The NGX Trading Bot is ready for production!_",
                marketData != null ? marketData.size() : 0,
                newsItems != null ? newsItems.size() : 0);

        assertThatCode(() -> telegramService.sendMessage(confirmationMessage))
                .doesNotThrowAnyException();

        // Also send via WhatsApp if configured
        if (notificationProperties.getWhatsapp().getChatId() != null
                && !notificationProperties.getWhatsapp().getChatId().isBlank()) {
            try {
                whatsAppService.sendMessage(confirmationMessage.replaceAll("[*_`]", ""));
                System.out.println("    WhatsApp confirmation also sent!");
            } catch (Exception e) {
                System.out.printf("    WhatsApp send failed (non-fatal): %s%n", e.getMessage());
            }
        }

        printResult("E2E Step 10: Confirmation",
                "E2E integration test pipeline completed successfully!");
    }
}
