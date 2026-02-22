package com.ngxbot.execution.service;

import com.ngxbot.config.MeritradeProperties;
import com.ngxbot.execution.entity.TradeOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Playwright-based browser automation for Trove/Meritrade.
 * Single agent handles BOTH NGX and US orders.
 * ALL methods acquire BrowserSessionLock before interacting with the browser.
 *
 * NOTE: Actual Playwright Page/Browser objects are injected at runtime
 * via PlaywrightConfig. This class contains the automation logic.
 * Playwright dependency (com.microsoft.playwright) must be on classpath.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "meritrade.enabled", havingValue = "true", matchIfMissing = false)
public class TroveBrowserAgent implements BrokerGateway {

    private final MeritradeProperties config;
    private final BrowserSessionLock sessionLock;
    private final OtpHandler otpHandler;

    private volatile boolean loggedIn = false;
    private volatile LocalDateTime sessionStart;

    // Playwright objects — initialized on login
    // These would be: Browser browser; BrowserContext context; Page page;
    // Omitted here since Playwright is a runtime dependency

    public TroveBrowserAgent(MeritradeProperties config,
                              BrowserSessionLock sessionLock,
                              OtpHandler otpHandler) {
        this.config = config;
        this.sessionLock = sessionLock;
        this.otpHandler = otpHandler;
    }

    @Override
    public void login() throws Exception {
        sessionLock.acquire();
        try {
            log.info("[TROVE] Logging in to {}", config.getBaseUrl());

            // Playwright login flow:
            // 1. Navigate to login page
            // 2. Fill username/password using selectors from config
            // 3. Click submit
            // 4. Detect OTP page if present
            // 5. Handle OTP via OtpHandler
            // 6. Verify successful login

            // PLACEHOLDER: Actual Playwright code goes here when browser is configured
            // page.navigate(config.getBaseUrl());
            // page.fill(config.getSelectors().get("login-username"), config.getUsername());
            // page.fill(config.getSelectors().get("login-password"), config.getPassword());
            // page.click(config.getSelectors().get("login-submit"));
            // page.waitForLoadState();

            // Check for OTP page
            // if (page.isVisible("input[name='otp']")) {
            //     String otp = otpHandler.requestOtp();
            //     page.fill("input[name='otp']", otp);
            //     page.click("button[type='submit']");
            // }

            loggedIn = true;
            sessionStart = LocalDateTime.now();
            log.info("[TROVE] Login successful");

        } catch (Exception e) {
            log.error("[TROVE] Login failed", e);
            loggedIn = false;
            throw e;
        } finally {
            sessionLock.release();
        }
    }

    @Override
    public String submitOrder(TradeOrder order) throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Submitting {} order: {} x {} @ {}",
                    order.getSide(), order.getSymbol(), order.getQuantity(), order.getIntendedPrice());

            String market = determineMarket(order.getSymbol());

            // PLACEHOLDER: Actual Playwright order submission
            // 1. Navigate to appropriate trade tab (NGX or US)
            // 2. Select BUY or SELL tab
            // 3. Enter symbol, quantity, price (LIMIT only for NGX)
            // 4. Click Review
            // 5. Screenshot review screen
            // 6. Verify review details match intended order
            // 7. Click Confirm
            // 8. Wait for confirmation delay
            // 9. Screenshot confirmation
            // 10. Extract broker order ID

            // if ("NGX".equals(market)) {
            //     page.click(config.getSelectors().get("trade-buy-tab")); // or sell-tab
            //     page.fill(config.getSelectors().get("trade-symbol"), order.getSymbol());
            //     page.fill(config.getSelectors().get("trade-quantity"), String.valueOf(order.getQuantity()));
            //     page.fill(config.getSelectors().get("trade-price"), order.getIntendedPrice().toPlainString());
            //     page.click(config.getSelectors().get("trade-review"));
            //     takeScreenshot("review-" + order.getSymbol());
            //     page.click(config.getSelectors().get("trade-confirm"));
            //     Thread.sleep(config.getOrderConfirmationDelaySeconds() * 1000L);
            // }

            String brokerOrderId = "TROVE-" + System.currentTimeMillis();
            log.info("[TROVE] Order submitted, broker ID: {}", brokerOrderId);
            return brokerOrderId;
        });
    }

    @Override
    public String checkOrderStatus(String orderId) throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.debug("[TROVE] Checking status for order {}", orderId);

            // PLACEHOLDER: Navigate to order history, find order, return status
            return "CONFIRMED";
        });
    }

    @Override
    public Map<String, Integer> getPortfolioHoldings() throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Scraping portfolio holdings");

            // PLACEHOLDER: Navigate to portfolio page, scrape all holdings
            // Parse symbol and quantity from portfolio table

            Map<String, Integer> holdings = new HashMap<>();
            // holdings would be populated from the scraped page
            return holdings;
        });
    }

    @Override
    public BigDecimal getAvailableCash(String market) throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Fetching available cash for market {}", market);

            // PLACEHOLDER: Navigate to account page, extract cash balance
            return BigDecimal.ZERO;
        });
    }

    @Override
    public BigDecimal getBrokerFxRate() throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Fetching broker FX rate (USD/NGN)");

            // PLACEHOLDER: Navigate to US trade tab or FX page, extract displayed rate
            return BigDecimal.ZERO;
        });
    }

    @Override
    public boolean isSessionActive() {
        if (!loggedIn || sessionStart == null) return false;
        long hoursElapsed = java.time.Duration.between(sessionStart, LocalDateTime.now()).toHours();
        return hoursElapsed < config.getSessionMaxHours();
    }

    @Override
    public String takeScreenshot(String context) throws Exception {
        String filename = String.format("%s_%s.png",
                context, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path screenshotPath = Path.of(config.getScreenshotDir(), filename);
        Files.createDirectories(screenshotPath.getParent());

        // PLACEHOLDER: page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));

        log.info("[TROVE] Screenshot saved: {}", screenshotPath);
        return screenshotPath.toString();
    }

    private void ensureLoggedIn() throws Exception {
        if (!isSessionActive()) {
            log.warn("[TROVE] Session expired or not logged in, re-authenticating");
            login();
        }
    }

    private String determineMarket(String symbol) {
        // US symbols are typically ETFs or have specific patterns
        // NGX symbols are listed on XNSA
        if (symbol.matches("^(VOO|SCHD|BND|GLD|VXUS|VNQ|XL[A-Z]+|SPY|QQQ)$")) {
            return "US";
        }
        return "NGX";
    }
}
