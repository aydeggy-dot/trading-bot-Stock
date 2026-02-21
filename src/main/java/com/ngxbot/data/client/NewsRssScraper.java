package com.ngxbot.data.client;

import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRssScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 NGXTradingBot/1.0";

    // Nigerian financial news RSS feeds
    private static final Map<String, String> RSS_FEEDS = Map.of(
            "Nairametrics", "https://nairametrics.com/feed/",
            "BusinessDay", "https://businessday.ng/feed/"
    );

    // Stock symbols to look for in headlines
    private static final Set<String> WATCHED_SYMBOLS = Set.of(
            "ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "FBNH",
            "DANGCEM", "BUACEMENT", "SEPLAT", "ARADEL", "MTNN",
            "STANBICETF30", "VETGRIF30", "MERGROWTH", "MERVALUE",
            "ZENITH", "ACCESS", "DANGOTE", "MTN", "SEPLAT",
            // Common name variants
            "NGX", "NSE", "STOCK", "DIVIDEND", "EARNINGS"
    );

    private final NewsItemRepository newsItemRepository;

    /**
     * Scrape all configured RSS feeds for financial news.
     * Runs at 9:00 AM WAT (pre-market scan).
     */
    public List<NewsItem> scrapeAllFeeds() {
        log.info("Starting pre-market news scan from {} RSS feeds", RSS_FEEDS.size());
        List<NewsItem> allItems = new ArrayList<>();

        for (Map.Entry<String, String> feed : RSS_FEEDS.entrySet()) {
            try {
                List<NewsItem> items = scrapeFeed(feed.getKey(), feed.getValue());
                allItems.addAll(items);
            } catch (Exception e) {
                log.error("Failed to scrape RSS feed {}: {}", feed.getKey(), e.getMessage());
            }
        }

        log.info("News scan complete: {} new items from all feeds", allItems.size());
        return allItems;
    }

    /**
     * Scrape a single RSS feed and persist new items.
     */
    public List<NewsItem> scrapeFeed(String sourceName, String feedUrl) {
        log.info("Scraping RSS feed: {} ({})", sourceName, feedUrl);

        try {
            Document doc = Jsoup.connect(feedUrl)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .parser(Parser.xmlParser())
                    .get();

            Elements items = doc.select("item");
            List<NewsItem> savedItems = new ArrayList<>();

            for (Element item : items) {
                String title = item.selectFirst("title") != null
                        ? item.selectFirst("title").text() : "";
                String link = item.selectFirst("link") != null
                        ? item.selectFirst("link").text() : "";
                String pubDate = item.selectFirst("pubDate") != null
                        ? item.selectFirst("pubDate").text() : "";
                String description = item.selectFirst("description") != null
                        ? item.selectFirst("description").text() : "";

                if (title.isEmpty() || link.isEmpty()) continue;

                // Skip if already stored
                if (newsItemRepository.existsByUrl(link)) continue;

                // Extract mentioned symbols
                String[] mentionedSymbols = extractMentionedSymbols(title + " " + description);

                // Calculate relevance score based on symbol mentions
                int relevanceScore = calculateRelevance(title, description);

                NewsItem newsItem = NewsItem.builder()
                        .title(truncate(title, 500))
                        .source(sourceName)
                        .url(truncate(link, 1000))
                        .publishedAt(parseRssDate(pubDate))
                        .symbols(mentionedSymbols.length > 0 ? mentionedSymbols : null)
                        .relevanceScore(relevanceScore)
                        .summary(truncate(cleanHtml(description), 500))
                        .isProcessed(false)
                        .createdAt(LocalDateTime.now())
                        .build();

                savedItems.add(newsItemRepository.save(newsItem));
            }

            log.info("Scraped {} new items from {}", savedItems.size(), sourceName);
            return savedItems;

        } catch (Exception e) {
            log.error("Error scraping feed {} ({}): {}", sourceName, feedUrl, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Extract stock symbols mentioned in text.
     */
    private String[] extractMentionedSymbols(String text) {
        if (text == null) return new String[0];
        String upper = text.toUpperCase();
        return WATCHED_SYMBOLS.stream()
                .filter(upper::contains)
                .toArray(String[]::new);
    }

    /**
     * Calculate relevance score (0-100) for market-moving potential.
     */
    private int calculateRelevance(String title, String description) {
        String combined = (title + " " + description).toUpperCase();
        int score = 0;

        // High-impact keywords
        String[] highImpact = {"DIVIDEND", "EARNINGS", "PROFIT", "LOSS", "ACQUISITION",
                "MERGER", "RIGHTS ISSUE", "BONUS", "STOCK SPLIT", "SUSPENSION"};
        for (String keyword : highImpact) {
            if (combined.contains(keyword)) score += 20;
        }

        // Medium-impact keywords
        String[] mediumImpact = {"NGX", "NSE", "STOCK MARKET", "SHARE PRICE",
                "MARKET CAP", "SECTOR", "BANKING", "QUARTERLY RESULTS"};
        for (String keyword : mediumImpact) {
            if (combined.contains(keyword)) score += 10;
        }

        // Symbol mentions
        long symbolMentions = WATCHED_SYMBOLS.stream()
                .filter(combined::contains)
                .count();
        score += (int) (symbolMentions * 5);

        return Math.min(score, 100);
    }

    /**
     * Parse RSS date format (RFC 822): "Sat, 15 Feb 2026 10:30:00 +0100"
     */
    private LocalDateTime parseRssDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.trace("Could not parse RSS date: {}", dateStr);
            return null;
        }
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        return Jsoup.parse(html).text();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
