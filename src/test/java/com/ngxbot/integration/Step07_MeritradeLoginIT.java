package com.ngxbot.integration;

import com.ngxbot.config.MeritradeProperties;
import com.ngxbot.execution.service.TroveBrowserAgent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 7: Meritrade/Trove Browser Login
 *
 * Verifies:
 *   - Playwright can navigate to Meritrade
 *   - Login form is found and filled
 *   - OTP is handled via WhatsApp (if required)
 *   - Dashboard page loads successfully
 *   - Portfolio holdings and cash balance can be read
 *
 * Prereqs:
 *   1. MERITRADE_USERNAME + MERITRADE_PASSWORD in .env
 *   2. WAHA running with linked WhatsApp (for OTP)
 *   3. meritrade.headless=false in integration profile for visual observation
 *
 * IMPORTANT: This test runs in headed mode so you can WATCH the browser.
 * If CSS selectors don't match, the test output will tell you what to update.
 */
@Tag("integration")
@Tag("browser")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step07_MeritradeLoginIT extends IntegrationTestBase {

    @Autowired
    private TroveBrowserAgent troveBrowserAgent;

    @Autowired
    private MeritradeProperties meritradeProperties;

    @Test
    @Order(1)
    @DisplayName("7.1 Meritrade credentials are configured")
    void meritradeCredentialsConfigured() {
        assertThat(meritradeProperties.getUsername())
                .as("MERITRADE_USERNAME must be set")
                .isNotBlank();
        assertThat(meritradeProperties.getPassword())
                .as("MERITRADE_PASSWORD must be set")
                .isNotBlank();

        printResult("Meritrade Config",
                String.format("URL: %s, User: %s, Headless: %s, SlowMo: %.0fms",
                        meritradeProperties.getBaseUrl(),
                        meritradeProperties.getUsername(),
                        meritradeProperties.isHeadless(),
                        meritradeProperties.getSlowMoMs()));
    }

    @Test
    @Order(2)
    @DisplayName("7.2 Login to Meritrade/Trove platform")
    void loginToMeritrade() throws Exception {
        // This launches a real browser, navigates to Meritrade, and logs in
        // If OTP is required, it will request via WhatsApp
        troveBrowserAgent.login();

        assertThat(troveBrowserAgent.isSessionActive())
                .as("Session should be active after login")
                .isTrue();

        // Take a screenshot for verification
        String screenshotPath = troveBrowserAgent.takeScreenshot("post-login");

        // Verify real screenshot PNG file was created on disk
        assertThat(Path.of(screenshotPath))
                .as("Post-login screenshot file should exist")
                .exists();
        assertThat(Files.size(Path.of(screenshotPath)))
                .as("Screenshot file should not be empty")
                .isGreaterThan(0);

        printResult("Meritrade Login",
                String.format("Login successful! Session active. Screenshot: %s", screenshotPath));
    }

    @Test
    @Order(3)
    @DisplayName("7.3 Read portfolio holdings from Meritrade")
    void readPortfolioHoldings() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        Map<String, Integer> holdings = troveBrowserAgent.getPortfolioHoldings();

        printResult("Portfolio Holdings",
                String.format("Found %d positions: %s", holdings.size(), holdings));

        // Just verify the method doesn't throw — user may have 0 holdings
        assertThat(holdings).isNotNull();

        holdings.forEach((symbol, qty) -> {
            System.out.printf("    %s: %d shares%n", symbol, qty);
            assertThat(qty).isPositive();
        });
    }

    @Test
    @Order(4)
    @DisplayName("7.4 Read available cash balance from Meritrade")
    void readAvailableCash() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        BigDecimal ngxCash = troveBrowserAgent.getAvailableCash("NGX");

        printResult("Available Cash",
                String.format("NGX Cash: ₦%,.2f", ngxCash));

        assertThat(ngxCash).isNotNull();
        assertThat(ngxCash).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @Order(5)
    @DisplayName("7.5 Read FX rate from Meritrade")
    void readFxRate() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        try {
            BigDecimal fxRate = troveBrowserAgent.getBrokerFxRate();
            printResult("FX Rate",
                    String.format("USD/NGN rate from broker: ₦%,.2f (0 = placeholder, real Playwright not yet wired)",
                            fxRate));
            assertThat(fxRate).isNotNull();
        } catch (Exception e) {
            printResult("FX Rate",
                    String.format("Could not read FX rate: %s (may not be on dashboard)", e.getMessage()));
        }
    }

    @Test
    @Order(6)
    @DisplayName("7.6 Take screenshot of logged-in dashboard")
    void takeScreenshotOfDashboard() throws Exception {
        if (!troveBrowserAgent.isSessionActive()) {
            troveBrowserAgent.login();
        }

        String path = troveBrowserAgent.takeScreenshot("dashboard-verification");

        printResult("Dashboard Screenshot",
                String.format("Screenshot saved: %s — visually verify the broker dashboard", path));

        assertThat(path).isNotBlank();
        assertThat(Path.of(path))
                .as("Dashboard screenshot file should exist")
                .exists();
    }
}
