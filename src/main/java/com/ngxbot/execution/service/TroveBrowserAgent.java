package com.ngxbot.execution.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.ngxbot.config.MeritradeProperties;
import com.ngxbot.execution.entity.TradeOrder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright-based browser automation for Trove/Meritrade.
 * Single agent handles BOTH NGX and US orders.
 * ALL methods acquire BrowserSessionLock before interacting with the browser.
 *
 * Browser (Chromium process) is injected from PlaywrightConfig.
 * BrowserContext + Page are created per-session in login() and cleaned up on logout/destroy.
 *
 * All CSS selectors are externalized in meritrade.selectors (application.yml).
 * After running the integration test in headed mode, review screenshots to correct selectors.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "meritrade.enabled", havingValue = "true", matchIfMissing = false)
public class TroveBrowserAgent implements BrokerGateway {

    private final MeritradeProperties config;
    private final BrowserSessionLock sessionLock;
    private final OtpHandler otpHandler;
    private final Browser browser;

    private volatile boolean loggedIn = false;
    private volatile LocalDateTime sessionStart;

    // Per-session Playwright objects — created fresh on each login.
    // Guarded by sessionLock for mutations; volatile for safe reads from isSessionActive().
    private volatile BrowserContext context;
    private volatile Page page;

    /** Pattern to extract a decimal number from text like "₦1,234,567.89" or "$1,234.56". */
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[\\d,]+\\.?\\d*");

    public TroveBrowserAgent(MeritradeProperties config,
                              BrowserSessionLock sessionLock,
                              OtpHandler otpHandler,
                              Browser browser) {
        this.config = config;
        this.sessionLock = sessionLock;
        this.otpHandler = otpHandler;
        this.browser = browser;
    }

    @Override
    public void login() throws Exception {
        sessionLock.acquire();
        try {
            log.info("[TROVE] Logging in to {}", config.getBaseUrl());
            Map<String, String> sel = config.getSelectors();

            // Clean up old screenshots before starting a new session
            cleanupOldScreenshots();

            // 1. Close any stale context/page from a previous session
            closePageAndContext();

            // 2. Create fresh BrowserContext + Page
            context = browser.newContext();
            page = context.newPage();
            page.setDefaultNavigationTimeout(config.getNavigationTimeoutMs());
            page.setDefaultTimeout(config.getNavigationTimeoutMs());
            log.debug("[TROVE] New browser context and page created (navTimeout={}ms)",
                    config.getNavigationTimeoutMs());

            // 3. Navigate to login page
            page.navigate(config.getBaseUrl());
            page.waitForLoadState(LoadState.NETWORKIDLE);
            takeScreenshot("pre-login");

            // 4. Fill credentials
            page.fill(sel.get("login-username"), config.getUsername());
            page.fill(sel.get("login-password"), config.getPassword());
            log.debug("[TROVE] Credentials filled");

            // 5. Submit login form
            page.click(sel.get("login-submit"));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 6. Detect OTP page (wait up to 5s for OTP input to appear)
            String otpInputSelector = sel.getOrDefault("otp-input", "input[name='otp']");
            String otpSubmitSelector = sel.getOrDefault("otp-submit", "button[type='submit']");
            Locator otpInput = page.locator(otpInputSelector);

            try {
                otpInput.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                if (otpInput.isVisible()) {
                    log.info("[TROVE] OTP page detected — requesting OTP via WhatsApp");
                    takeScreenshot("otp-prompt");

                    String otp = otpHandler.requestOtp();
                    otpInput.fill(otp);
                    page.click(otpSubmitSelector);
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    log.info("[TROVE] OTP submitted");
                }
            } catch (com.microsoft.playwright.TimeoutError e) {
                // OTP input not found within 5s — no OTP required, continue
                log.debug("[TROVE] No OTP page detected (timeout), proceeding");
            }

            // 7. Verify login succeeded — the login form should no longer be visible
            Locator loginForm = page.locator(sel.get("login-submit"));
            boolean stillOnLoginPage = false;
            try {
                stillOnLoginPage = loginForm.isVisible();
            } catch (Exception ignored) {
                // Element not found = good, we navigated away from login
            }
            if (stillOnLoginPage) {
                takeScreenshot("login-failed-still-on-login");
                throw new IllegalStateException(
                        "Login verification failed — still on login page. Check credentials or review screenshot.");
            }

            // 8. Post-login screenshot
            takeScreenshot("post-login");

            loggedIn = true;
            sessionStart = LocalDateTime.now();
            log.info("[TROVE] Login successful");

        } catch (Exception e) {
            log.error("[TROVE] Login failed", e);
            loggedIn = false;
            takeScreenshotSafe("login-error");
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

            // Order submission is NOT yet implemented — trade form selectors must be
            // manually verified against the real broker UI before enabling this path.
            // Returning a fake ID would cause the bot to believe orders are placed.
            throw new UnsupportedOperationException(
                    "Order submission is not yet implemented. "
                    + "Trade form selectors must be validated before enabling real order placement.");
        });
    }

    @Override
    public String checkOrderStatus(String orderId) throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.debug("[TROVE] Checking status for order {}", orderId);

            // Order status checking is not yet implemented — selectors need validation.
            throw new UnsupportedOperationException(
                    "Order status checking is not yet implemented. "
                    + "Order history selectors must be validated before enabling.");
        });
    }

    @Override
    public Map<String, Integer> getPortfolioHoldings() throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Scraping portfolio holdings");
            Map<String, String> sel = config.getSelectors();

            Map<String, Integer> holdings = new HashMap<>();

            try {
                // Navigate to portfolio section using configured selector
                String portfolioLinkSelector = sel.get("portfolio-link");
                if (portfolioLinkSelector != null) {
                    Locator portfolioLink = page.locator(portfolioLinkSelector).first();
                    try {
                        portfolioLink.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                        if (portfolioLink.isVisible()) {
                            portfolioLink.click();
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                        }
                    } catch (com.microsoft.playwright.TimeoutError e) {
                        log.debug("[TROVE] Portfolio link not found — may already be on portfolio page");
                    }
                }

                takeScreenshot("portfolio-page");

                // Scrape rows using configured selector
                String rowSelector = sel.getOrDefault("portfolio-rows", "table tbody tr");
                String symbolSelector = sel.getOrDefault("portfolio-symbol", "td:nth-child(1)");
                String quantitySelector = sel.getOrDefault("portfolio-quantity", "td:nth-child(3)");

                Locator rows = page.locator(rowSelector);
                int rowCount = rows.count();
                log.info("[TROVE] Found {} portfolio rows using selector '{}'", rowCount, rowSelector);

                for (int i = 0; i < rowCount; i++) {
                    try {
                        Locator row = rows.nth(i);
                        String fullText = row.textContent().trim();

                        // Extract symbol from configured column
                        String symbol = row.locator(symbolSelector).first().textContent().trim();
                        String qtyText = row.locator(quantitySelector).first().textContent().trim();

                        if (symbol.isEmpty()) {
                            log.debug("[TROVE] Row {} has empty symbol, skipping: {}", i, fullText);
                            continue;
                        }

                        // Parse quantity — strip commas, decimals
                        String cleanedQty = qtyText.replaceAll("[^0-9]", "");
                        if (cleanedQty.isEmpty()) {
                            log.debug("[TROVE] Row {} has unparseable quantity '{}', skipping", i, qtyText);
                            continue;
                        }

                        int quantity = Integer.parseInt(cleanedQty);
                        if (quantity > 0) {
                            // Normalize symbol — strip whitespace, uppercase
                            String normalizedSymbol = symbol.replaceAll("\\s+", "").toUpperCase();
                            holdings.put(normalizedSymbol, quantity);
                            log.info("[TROVE] Holding: {} x {}", normalizedSymbol, quantity);
                        }
                    } catch (Exception e) {
                        log.debug("[TROVE] Could not parse portfolio row {}: {}", i, e.getMessage());
                    }
                }

                if (rowCount == 0) {
                    log.warn("[TROVE] No portfolio rows found with selector '{}'. "
                            + "Review portfolio-page screenshot and update meritrade.selectors.portfolio-rows",
                            rowSelector);
                }

            } catch (Exception e) {
                log.warn("[TROVE] Portfolio scraping failed (soft failure): {}. "
                        + "Returning empty holdings. Review screenshots to update selectors.", e.getMessage());
                takeScreenshotSafe("portfolio-error");
            }

            return holdings;
        });
    }

    @Override
    public BigDecimal getAvailableCash(String market) throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Fetching available cash for market {}", market);
            Map<String, String> sel = config.getSelectors();

            try {
                String cashSelector = sel.getOrDefault("cash-balance",
                        "[class*='balance'], [class*='cash-available']");

                Locator cashElements = page.locator(cashSelector);
                takeScreenshot("cash-balance-" + market);

                int count = cashElements.count();
                log.debug("[TROVE] Found {} cash balance elements using selector '{}'", count, cashSelector);

                for (int i = 0; i < count; i++) {
                    String text = cashElements.nth(i).textContent().trim();
                    log.debug("[TROVE] Cash element {}: '{}'", i, text);

                    BigDecimal value = parseCurrencyValue(text);
                    if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("[TROVE] Parsed cash balance for {}: {}", market, value);
                        return value;
                    }
                }

                log.warn("[TROVE] Could not parse cash balance with selector '{}'. "
                        + "Review cash-balance screenshot and update meritrade.selectors.cash-balance",
                        cashSelector);

            } catch (Exception e) {
                log.warn("[TROVE] Cash balance scraping failed (soft failure): {}. "
                        + "Returning ZERO.", e.getMessage());
                takeScreenshotSafe("cash-error-" + market);
            }

            return BigDecimal.ZERO;
        });
    }

    @Override
    public BigDecimal getBrokerFxRate() throws Exception {
        return sessionLock.executeWithLock(() -> {
            ensureLoggedIn();
            log.info("[TROVE] Fetching broker FX rate (USD/NGN)");
            Map<String, String> sel = config.getSelectors();

            try {
                String fxSelector = sel.getOrDefault("fx-rate",
                        "[class*='fx-rate'], [class*='exchange-rate']");

                Locator fxElements = page.locator(fxSelector);
                int count = fxElements.count();
                log.debug("[TROVE] Found {} FX rate elements using selector '{}'", count, fxSelector);

                for (int i = 0; i < count; i++) {
                    String text = fxElements.nth(i).textContent().trim();
                    log.debug("[TROVE] FX element {}: '{}'", i, text);

                    BigDecimal value = parseCurrencyValue(text);
                    // Sanity: USD/NGN rate should be between 100 and 5000
                    if (value != null
                            && value.compareTo(new BigDecimal("100")) >= 0
                            && value.compareTo(new BigDecimal("5000")) <= 0) {
                        log.info("[TROVE] Parsed FX rate: {}", value);
                        return value;
                    }
                }

                log.warn("[TROVE] Could not find FX rate with selector '{}'. "
                        + "Review dashboard screenshot and update meritrade.selectors.fx-rate",
                        fxSelector);

            } catch (Exception e) {
                log.warn("[TROVE] FX rate scraping failed (soft failure): {}. "
                        + "Returning ZERO.", e.getMessage());
            }

            return BigDecimal.ZERO;
        });
    }

    @Override
    public boolean isSessionActive() {
        if (!loggedIn || sessionStart == null || page == null) return false;
        try {
            // Check both time-based expiry and browser page health
            long hoursElapsed = Duration.between(sessionStart, LocalDateTime.now()).toHours();
            return hoursElapsed < config.getSessionMaxHours() && !page.isClosed();
        } catch (Exception e) {
            log.warn("[TROVE] Session check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String takeScreenshot(String context) throws Exception {
        String filename = String.format("%s_%s.png",
                context, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path screenshotPath = Path.of(config.getScreenshotDir(), filename);
        Files.createDirectories(screenshotPath.getParent());

        if (page != null && !page.isClosed()) {
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotPath)
                    .setFullPage(true));
            log.info("[TROVE] Screenshot saved: {}", screenshotPath);
        } else {
            log.warn("[TROVE] Cannot take screenshot — page is null or closed");
        }

        return screenshotPath.toString();
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    private void ensureLoggedIn() throws Exception {
        if (!isSessionActive()) {
            log.warn("[TROVE] Session expired or not logged in, re-authenticating");
            login();
        }
    }

    private String determineMarket(String symbol) {
        if (symbol.matches("^(VOO|SCHD|BND|GLD|VXUS|VNQ|XL[A-Z]+|SPY|QQQ)$")) {
            return "US";
        }
        return "NGX";
    }

    /**
     * Extracts the first decimal number from text containing currency formatting.
     * Handles: "₦1,234,567.89", "$1,234.56", "NGN 500,000", "1500.75", etc.
     * Returns null if no number found.
     */
    private BigDecimal parseCurrencyValue(String text) {
        if (text == null || text.isEmpty()) return null;

        Matcher matcher = CURRENCY_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group().replaceAll(",", "");
            if (match.isEmpty() || match.equals(".")) continue;
            try {
                return new BigDecimal(match);
            } catch (NumberFormatException ignored) {
                // try next match
            }
        }
        return null;
    }

    /**
     * Takes a screenshot without throwing — used in error-handling paths.
     */
    private void takeScreenshotSafe(String context) {
        try {
            takeScreenshot(context);
        } catch (Exception e) {
            log.debug("[TROVE] Could not take error screenshot: {}", e.getMessage());
        }
    }

    /**
     * Deletes screenshot files older than the configured retention period.
     * Runs at the start of each login to prevent disk space accumulation.
     */
    private void cleanupOldScreenshots() {
        int retentionHours = config.getScreenshotRetentionHours();
        if (retentionHours <= 0) return;

        Path screenshotDir = Path.of(config.getScreenshotDir());
        if (!Files.isDirectory(screenshotDir)) return;

        Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));
        int deleted = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(screenshotDir, "*.png")) {
            for (Path file : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.creationTime().toInstant().isBefore(cutoff)) {
                        Files.delete(file);
                        deleted++;
                    }
                } catch (IOException e) {
                    log.debug("[TROVE] Could not delete old screenshot {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("[TROVE] Could not scan screenshot directory for cleanup: {}", e.getMessage());
        }

        if (deleted > 0) {
            log.info("[TROVE] Cleaned up {} screenshots older than {}h", deleted, retentionHours);
        }
    }

    /**
     * Closes the current Page and BrowserContext if they exist.
     */
    private void closePageAndContext() {
        if (page != null) {
            try {
                if (!page.isClosed()) page.close();
            } catch (Exception e) {
                log.debug("[TROVE] Error closing page: {}", e.getMessage());
            }
            page = null;
        }
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                log.debug("[TROVE] Error closing context: {}", e.getMessage());
            }
            context = null;
        }
    }

    @PreDestroy
    public void closeBrowser() {
        log.info("[TROVE] Shutting down — closing browser context and page");
        sessionLock.acquire();
        try {
            closePageAndContext();
            loggedIn = false;
        } finally {
            sessionLock.release();
        }
    }
}
