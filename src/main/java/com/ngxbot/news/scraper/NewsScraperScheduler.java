package com.ngxbot.news.scraper;

import com.ngxbot.data.entity.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler that orchestrates all news scrapers at scheduled intervals throughout
 * the trading day. Runs on weekdays (MON-FRI) in Africa/Lagos timezone (WAT, UTC+1).
 *
 * <p>Schedule:</p>
 * <ul>
 *   <li><b>08:00 WAT</b> - Early morning scan: All 5 web scrapers run to gather
 *       overnight and pre-market news before the NGX opens at 10:00.</li>
 *   <li><b>12:30 WAT</b> - Mid-day scan: All 5 web scrapers run again during the
 *       trading session to catch breaking news and midday updates.</li>
 *   <li><b>17:00 WAT</b> - After-market scan: All 5 web scrapers + NGX bulletin
 *       parser run to capture post-close announcements, corporate actions, and
 *       the daily NGX bulletin PDF.</li>
 * </ul>
 *
 * <p>Each scraper is isolated so that a failure in one does not prevent the others
 * from running. Errors are logged and the scheduler continues with the next source.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsScraperScheduler {

    private final NairametricsScraper nairametricsScraper;
    private final BusinessDayScraper businessDayScraper;
    private final ReutersRssScraper reutersRssScraper;
    private final SeekingAlphaScraper seekingAlphaScraper;
    private final CbnPressScraper cbnPressScraper;
    private final NgxBulletinParser ngxBulletinParser;

    /**
     * Early morning news scan at 08:00 WAT (Monday-Friday).
     * Runs all 5 web scrapers to gather pre-market news before the NGX opens at 10:00.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Africa/Lagos")
    public void earlyMorningNewsScan() {
        log.info("=== EARLY MORNING NEWS SCAN STARTED (08:00 WAT) ===");
        List<NewsItem> allItems = runAllWebScrapers();
        log.info("=== EARLY MORNING NEWS SCAN COMPLETE — {} total items ===", allItems.size());
    }

    /**
     * Mid-day news scan at 12:30 WAT (Monday-Friday).
     * Runs all 5 web scrapers to catch breaking news during the trading session.
     */
    @Scheduled(cron = "0 30 12 * * MON-FRI", zone = "Africa/Lagos")
    public void midDayNewsScan() {
        log.info("=== MID-DAY NEWS SCAN STARTED (12:30 WAT) ===");
        List<NewsItem> allItems = runAllWebScrapers();
        log.info("=== MID-DAY NEWS SCAN COMPLETE — {} total items ===", allItems.size());
    }

    /**
     * After-market news scan at 17:00 WAT (Monday-Friday).
     * Runs all 5 web scrapers plus the NGX bulletin parser for post-close content.
     * The NGX bulletin PDF is typically published after market close and contains
     * corporate actions, dividend declarations, and trading suspensions.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Africa/Lagos")
    public void afterMarketNewsScan() {
        log.info("=== AFTER-MARKET NEWS SCAN STARTED (17:00 WAT) ===");

        // Run all web scrapers
        List<NewsItem> allItems = runAllWebScrapers();

        // Additionally run the NGX bulletin parser for the day's bulletin
        int bulletinCount = runNgxBulletinParser();

        log.info("=== AFTER-MARKET NEWS SCAN COMPLETE — {} web items + {} bulletin items ===",
                allItems.size(), bulletinCount);
    }

    /**
     * Run all 5 web scrapers, collecting results and handling failures gracefully.
     * Each scraper runs independently; a failure in one does not affect the others.
     *
     * @return combined list of all newly persisted NewsItem entities
     */
    private List<NewsItem> runAllWebScrapers() {
        List<NewsItem> allItems = new ArrayList<>();

        // Nairametrics
        allItems.addAll(runScraper("Nairametrics", () -> nairametricsScraper.scrapeLatestArticles()));

        // BusinessDay
        allItems.addAll(runScraper("BusinessDay", () -> businessDayScraper.scrapeLatestArticles()));

        // Reuters Africa RSS
        allItems.addAll(runScraper("Reuters", () -> reutersRssScraper.scrapeLatestArticles()));

        // Seeking Alpha
        allItems.addAll(runScraper("SeekingAlpha", () -> seekingAlphaScraper.scrapeLatestArticles()));

        // Central Bank of Nigeria
        allItems.addAll(runScraper("CBN", () -> cbnPressScraper.scrapeLatestReleases()));

        return allItems;
    }

    /**
     * Run a single scraper with error handling and logging.
     *
     * @param sourceName human-readable name of the source for logging
     * @param scrapeAction the scraper action to execute
     * @return list of items from the scraper, or empty list on failure
     */
    private List<NewsItem> runScraper(String sourceName, ScraperAction scrapeAction) {
        try {
            List<NewsItem> items = scrapeAction.execute();
            log.info("  {} — {} new items", sourceName, items.size());
            return items;
        } catch (Exception e) {
            log.error("  {} — FAILED: {}", sourceName, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Run the NGX bulletin parser. This attempts to download and parse the daily
     * NGX bulletin PDF. The bulletin URL follows a predictable pattern but may
     * vary. Currently logs a placeholder; the actual URL discovery logic can be
     * enhanced to scrape the NGX website for the day's bulletin link.
     *
     * @return count of items extracted from the bulletin
     */
    private int runNgxBulletinParser() {
        try {
            // The NGX daily bulletin URL typically follows patterns like:
            // https://ngxgroup.com/exchange/data/daily-bulletin/
            // The actual PDF URL needs to be discovered from the NGX bulletin page
            String bulletinPageUrl = "https://ngxgroup.com/exchange/data/daily-bulletin/";

            log.info("  NGX_BULLETIN — Checking for daily bulletin at: {}", bulletinPageUrl);

            // Attempt to discover and download the bulletin PDF
            // This is a best-effort operation; the bulletin may not be available yet
            List<NewsItem> bulletinItems = ngxBulletinParser.downloadAndParseBulletin(bulletinPageUrl);
            log.info("  NGX_BULLETIN — {} items extracted from daily bulletin", bulletinItems.size());
            return bulletinItems.size();

        } catch (Exception e) {
            log.error("  NGX_BULLETIN — FAILED: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Functional interface for scraper actions, enabling uniform error handling.
     */
    @FunctionalInterface
    private interface ScraperAction {
        List<NewsItem> execute();
    }
}
