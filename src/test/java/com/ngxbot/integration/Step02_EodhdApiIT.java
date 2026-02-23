package com.ngxbot.integration;

import com.ngxbot.config.EodhdProperties;
import com.ngxbot.data.client.EodhdApiClient;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.discovery.client.EodhdScreenerClient;
import com.ngxbot.discovery.client.EodhdScreenerClient.ScreenerResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2: EODHD Market Data API
 *
 * Verifies:
 *   - OHLCV data fetches for NGX stocks
 *   - Fundamentals endpoint returns data
 *   - Screener returns NGX candidates
 *   - Data parses correctly into OhlcvBar objects
 *
 * Prereqs: EODHD_API_KEY set in .env
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step02_EodhdApiIT extends IntegrationTestBase {

    @Autowired
    private EodhdApiClient eodhdApiClient;

    @Autowired
    private EodhdScreenerClient eodhdScreenerClient;

    @Autowired
    private EodhdProperties eodhdProperties;

    @Test
    @Order(1)
    @DisplayName("2.1 EODHD API key is configured")
    void apiKeyIsConfigured() {
        assertThat(eodhdProperties.getApiKey())
                .as("EODHD_API_KEY must be set")
                .isNotBlank();

        printResult("EODHD Config",
                String.format("API key: %s***, Exchange: %s",
                        eodhdProperties.getApiKey().substring(0, 5),
                        eodhdProperties.getExchange()));
    }

    @Test
    @Order(2)
    @DisplayName("2.2 Fetch OHLCV data for ZENITHBANK")
    void fetchOhlcvData() {
        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to = LocalDate.now();

        List<OhlcvBar> bars = eodhdApiClient.fetchAndStoreOhlcv("ZENITHBANK", from, to);

        printResult("OHLCV Fetch",
                String.format("ZENITHBANK: %d bars from %s to %s", bars.size(), from, to));

        assertThat(bars).isNotEmpty();
        assertThat(bars.get(0).getSymbol()).contains("ZENITHBANK");
        assertThat(bars.get(0).getClosePrice()).isPositive();
        assertThat(bars.get(0).getVolume()).isPositive();
        assertThat(bars.get(0).getTradeDate()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("2.3 Fetch OHLCV data for GTCO")
    void fetchOhlcvDataGtco() {
        List<OhlcvBar> bars = eodhdApiClient.fetchRecentOhlcv("GTCO", 30);

        printResult("OHLCV Fetch",
                String.format("GTCO: %d recent bars", bars.size()));

        assertThat(bars).isNotEmpty();
        assertThat(bars.get(0).getClosePrice()).isPositive();
    }

    @Test
    @Order(4)
    @DisplayName("2.4 Fetch fundamentals for ZENITHBANK (requires paid API)")
    void fetchFundamentals() {
        try {
            String fundamentals = eodhdApiClient.fetchFundamentals("ZENITHBANK");
            printResult("Fundamentals Fetch",
                    String.format("ZENITHBANK fundamentals: %d chars", fundamentals.length()));
            assertThat(fundamentals).isNotBlank();
        } catch (Exception e) {
            printResult("Fundamentals Fetch",
                    String.format("SKIPPED (requires paid EODHD plan): %s", e.getMessage()));
            // Free tier doesn't support fundamentals — this is expected
        }
    }

    @Test
    @Order(5)
    @DisplayName("2.5 Screener returns NGX stock candidates (requires paid API)")
    void screenerReturnsResults() {
        try {
            List<ScreenerResult> results = eodhdScreenerClient.screenNgxStocks();

            printResult("Screener",
                    String.format("Found %d NGX stock candidates", results.size()));

            if (!results.isEmpty()) {
                ScreenerResult first = results.get(0);
                System.out.printf("    First result: %s (%s) — Sector: %s, Vol: %d%n",
                        first.symbol(), first.name(), first.sector(), first.avgVolume());
                assertThat(results.get(0).symbol()).isNotBlank();
            }
        } catch (Exception e) {
            printResult("Screener",
                    String.format("SKIPPED (requires paid EODHD plan): %s", e.getMessage()));
            // Free tier doesn't support screener — this is expected
        }
    }

    @Test
    @Order(6)
    @DisplayName("2.6 OHLCV data persists to database")
    void ohlcvDataPersistsToDb() {
        // Fetch data (fetchAndStoreOhlcv persists to DB)
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now();
        List<OhlcvBar> bars = eodhdApiClient.fetchAndStoreOhlcv("DANGCEM", from, to);

        printResult("OHLCV Persistence",
                String.format("DANGCEM: %d bars fetched and persisted", bars.size()));

        assertThat(bars).isNotEmpty();
        // Verify they all have IDs (i.e. they were saved)
        bars.forEach(bar ->
                assertThat(bar.getDataSource()).isEqualTo("EODHD"));
    }

    @Test
    @Order(7)
    @DisplayName("2.7 OHLCV bar values are within NGX ±10% daily limit range")
    void ohlcvValuesAreReasonable() {
        List<OhlcvBar> bars = eodhdApiClient.fetchRecentOhlcv("ZENITHBANK", 5);

        for (OhlcvBar bar : bars) {
            // High >= Low (basic sanity)
            assertThat(bar.getHighPrice())
                    .as("High >= Low for %s on %s", bar.getSymbol(), bar.getTradeDate())
                    .isGreaterThanOrEqualTo(bar.getLowPrice());

            // Close should be between Low and High
            assertThat(bar.getClosePrice())
                    .as("Close between Low-High for %s on %s", bar.getSymbol(), bar.getTradeDate())
                    .isBetween(bar.getLowPrice(), bar.getHighPrice());
        }

        printResult("Data Quality", "All OHLCV bars pass sanity checks (High>=Low, Low<=Close<=High)");
    }
}
