package com.ngxbot.data.scheduler;

import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.client.EodhdApiClient;
import com.ngxbot.data.client.EtfNavScraper;
import com.ngxbot.data.client.NewsRssScraper;
import com.ngxbot.data.client.NgxWebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectionScheduler {

    private final EodhdApiClient eodhdApiClient;
    private final NgxWebScraper ngxWebScraper;
    private final EtfNavScraper etfNavScraper;
    private final NewsRssScraper newsRssScraper;
    private final TradingProperties tradingProperties;

    /**
     * Pre-market news scan — 9:00 AM WAT, Monday-Friday.
     * Scans financial news RSS feeds for market-relevant headlines.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Africa/Lagos")
    public void preMarketNewsScan() {
        log.info("=== PRE-MARKET NEWS SCAN STARTED ===");
        try {
            var items = newsRssScraper.scrapeAllFeeds();
            log.info("=== PRE-MARKET NEWS SCAN COMPLETE — {} items ===", items.size());
        } catch (Exception e) {
            log.error("Pre-market news scan failed: {}", e.getMessage(), e);
        }
    }

    /**
     * EODHD OHLCV data pull — 3:00 PM WAT, Monday-Friday (after market close).
     * Fetches end-of-day price data for all watchlist stocks and ETFs.
     */
    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Africa/Lagos")
    public void pullEodhdData() {
        log.info("=== EODHD DATA PULL STARTED ===");
        try {
            List<String> allSymbols = getAllWatchlistSymbols();
            int successCount = 0;
            int failCount = 0;

            for (String symbol : allSymbols) {
                try {
                    var bars = eodhdApiClient.fetchRecentOhlcv(symbol, 5);
                    if (!bars.isEmpty()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("EODHD pull failed for {}: {}", symbol, e.getMessage());
                }
            }

            log.info("=== EODHD DATA PULL COMPLETE — {}/{} successful, {} failed ===",
                    successCount, allSymbols.size(), failCount);
        } catch (Exception e) {
            log.error("EODHD data pull failed: {}", e.getMessage(), e);
        }
    }

    /**
     * NGX website scrape (backup) — 3:05 PM WAT, Monday-Friday.
     * Runs 5 minutes after EODHD pull as a backup data source.
     * Only fills in gaps where EODHD data is missing.
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI", zone = "Africa/Lagos")
    public void scrapeNgxBackup() {
        log.info("=== NGX BACKUP SCRAPE STARTED ===");
        try {
            var bars = ngxWebScraper.scrapeDailyPrices();
            var asi = ngxWebScraper.scrapeAsiIndex();
            log.info("=== NGX BACKUP SCRAPE COMPLETE — {} bars, ASI: {} ===",
                    bars.size(), asi != null ? asi.getCloseValue() : "N/A");
        } catch (Exception e) {
            log.error("NGX backup scrape failed: {}", e.getMessage(), e);
        }
    }

    /**
     * ETF NAV scrape — 4:00 PM WAT, Monday-Friday.
     * Scrapes fund manager websites for ETF Net Asset Values.
     * NAV is published after market close, so this runs later.
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Africa/Lagos")
    public void scrapeEtfNavs() {
        log.info("=== ETF NAV SCRAPE STARTED ===");
        try {
            var valuations = etfNavScraper.scrapeAllEtfNavs();
            log.info("=== ETF NAV SCRAPE COMPLETE — {} valuations ===", valuations.size());
        } catch (Exception e) {
            log.error("ETF NAV scrape failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger: backfill historical data for a symbol.
     * Not scheduled — called manually via dashboard or startup.
     */
    public void backfillHistoricalData(String symbol, int days) {
        log.info("Backfilling {} days of historical data for {}", days, symbol);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        var bars = eodhdApiClient.fetchAndStoreOhlcv(symbol, from, to);
        log.info("Backfill complete for {}: {} bars stored", symbol, bars.size());
    }

    /**
     * Manual trigger: backfill all watchlist symbols.
     */
    public void backfillAllSymbols(int days) {
        log.info("Backfilling {} days for all watchlist symbols", days);
        for (String symbol : getAllWatchlistSymbols()) {
            try {
                backfillHistoricalData(symbol, days);
            } catch (Exception e) {
                log.error("Backfill failed for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private List<String> getAllWatchlistSymbols() {
        List<String> all = new ArrayList<>();
        all.addAll(tradingProperties.getWatchlist().getEtfs());
        all.addAll(tradingProperties.getWatchlist().getLargeCaps());
        return all;
    }
}
