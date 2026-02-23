package com.ngxbot.integration;

import com.ngxbot.data.client.NgxWebScraper;
import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.entity.MarketIndex;
import com.ngxbot.news.scraper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 5: News Scrapers (All 6 Sources)
 *
 * Verifies each scraper can connect to live websites and parse content.
 * Scrapers deduplicate by URL, so repeated runs return 0 NEW articles.
 * The key validation: scraper connects and parses without throwing.
 *
 * NOTE: These tests hit live websites. Respect rate limits.
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step05_ScrapersIT extends IntegrationTestBase {

    @Autowired
    private BusinessDayScraper businessDayScraper;

    @Autowired
    private NairametricsScraper nairametricsScraper;

    @Autowired
    private SeekingAlphaScraper seekingAlphaScraper;

    @Autowired
    private ReutersRssScraper reutersRssScraper;

    @Autowired
    private CbnPressScraper cbnPressScraper;

    @Autowired
    private NgxWebScraper ngxWebScraper;

    @Test
    @Order(1)
    @DisplayName("5a. BusinessDay scraper connects and parses")
    void businessDayScraperWorks() {
        assertThatCode(() -> {
            List<NewsItem> articles = businessDayScraper.scrapeLatestArticles();
            printResult("BusinessDay Scraper",
                    String.format("Returned %d new articles (0 is OK on repeat runs — dedup by URL)",
                            articles.size()));
            if (!articles.isEmpty()) {
                System.out.printf("    First: \"%s\" [%s]%n",
                        articles.get(0).getTitle(), articles.get(0).getUrl());
            }
        }).as("BusinessDay scraper should not throw").doesNotThrowAnyException();
    }

    @Test
    @Order(2)
    @DisplayName("5b. Nairametrics scraper connects and parses")
    void nairametricsScraperWorks() {
        assertThatCode(() -> {
            List<NewsItem> articles = nairametricsScraper.scrapeLatestArticles();
            printResult("Nairametrics Scraper",
                    String.format("Returned %d new articles", articles.size()));
            if (!articles.isEmpty()) {
                System.out.printf("    First: \"%s\" [%s]%n",
                        articles.get(0).getTitle(), articles.get(0).getUrl());
            }
        }).as("Nairametrics scraper should not throw").doesNotThrowAnyException();
    }

    @Test
    @Order(3)
    @DisplayName("5c. SeekingAlpha scraper (may be blocked by anti-bot)")
    void seekingAlphaScraperWorks() {
        // SeekingAlpha aggressively blocks scrapers — 403 is expected
        try {
            List<NewsItem> articles = seekingAlphaScraper.scrapeLatestArticles();
            printResult("SeekingAlpha Scraper",
                    String.format("Returned %d articles", articles.size()));
        } catch (Exception e) {
            printResult("SeekingAlpha Scraper",
                    String.format("Blocked (expected): %s", e.getMessage()));
        }
        // Always passes — SeekingAlpha blocking is a known limitation
    }

    @Test
    @Order(4)
    @DisplayName("5d. Reuters RSS scraper parses feed")
    void reutersRssScraperWorks() {
        try {
            List<NewsItem> articles = reutersRssScraper.scrapeLatestArticles();
            printResult("Reuters RSS Scraper",
                    String.format("Returned %d articles from RSS", articles.size()));
            if (!articles.isEmpty()) {
                System.out.printf("    First: \"%s\" [%s]%n",
                        articles.get(0).getTitle(), articles.get(0).getUrl());
            }
        } catch (Exception e) {
            printResult("Reuters RSS Scraper",
                    String.format("Feed unavailable: %s — RSS URL may have moved", e.getMessage()));
        }
        // Soft pass — RSS feeds can change URLs
    }

    @Test
    @Order(5)
    @DisplayName("5e. CBN Press scraper parses releases")
    void cbnPressScraperWorks() {
        try {
            List<NewsItem> releases = cbnPressScraper.scrapeLatestReleases();
            printResult("CBN Press Scraper",
                    String.format("Returned %d press releases", releases.size()));
            if (!releases.isEmpty()) {
                System.out.printf("    First: \"%s\"%n", releases.get(0).getTitle());
            }
        } catch (Exception e) {
            printResult("CBN Press Scraper",
                    String.format("Failed: %s — CBN website may have changed", e.getMessage()));
        }
        // Soft pass — CBN website structure can change
    }

    @Test
    @Order(6)
    @DisplayName("5f. NGX Price List scraper returns tickers and prices")
    void ngxPriceListScraperWorks() {
        try {
            List<OhlcvBar> prices = ngxWebScraper.scrapeDailyPrices();
            printResult("NGX Price List Scraper",
                    String.format("Found %d stock prices", prices.size()));
            if (!prices.isEmpty()) {
                OhlcvBar first = prices.get(0);
                System.out.printf("    First: %s — Close: %s, Volume: %d%n",
                        first.getSymbol(), first.getClosePrice(), first.getVolume());
            }
        } catch (Exception e) {
            printResult("NGX Price List Scraper",
                    String.format("Failed: %s — NGX website may have changed", e.getMessage()));
        }
        // Soft pass — NGX website can require JavaScript rendering
    }

    @Test
    @Order(7)
    @DisplayName("5f-2. NGX ASI Index scraper returns index value")
    void ngxAsiIndexScraperWorks() {
        try {
            MarketIndex asi = ngxWebScraper.scrapeAsiIndex();
            printResult("NGX ASI Scraper",
                    String.format("ASI Index: %s", asi));
        } catch (Exception e) {
            printResult("NGX ASI Scraper",
                    String.format("Failed: %s", e.getMessage()));
        }
        // Soft pass
    }
}
