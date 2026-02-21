package com.ngxbot.news.scraper;

import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Scrapes Nairametrics (https://nairametrics.com/) for Nigerian financial news.
 * Nairametrics is one of the leading financial news sources in Nigeria, covering
 * stock market updates, earnings reports, dividend announcements, and macro analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NairametricsScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 NGXTradingBot/1.0";
    private static final String BASE_URL = "https://nairametrics.com/";
    private static final String BUSINESS_URL = "https://nairametrics.com/category/business-news/";

    private static final Set<String> WATCHED_SYMBOLS = Set.of(
            "ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "FBNH",
            "DANGCEM", "BUACEMENT", "SEPLAT", "ARADEL", "MTNN",
            "STANBICETF30", "VETGRIF30", "MERGROWTH", "MERVALUE",
            "SIAMLETF40", "NEWGOLD",
            // Common name variants
            "ZENITH", "ACCESS", "DANGOTE", "MTN", "NESTLE", "AIRTEL"
    );

    private static final Set<String> HIGH_IMPACT_KEYWORDS = Set.of(
            "DIVIDEND", "EARNINGS", "PROFIT", "LOSS", "ACQUISITION",
            "MERGER", "RIGHTS ISSUE", "BONUS", "STOCK SPLIT", "SUSPENSION",
            "DELISTING", "IPO", "OFFER"
    );

    private static final Set<String> MEDIUM_IMPACT_KEYWORDS = Set.of(
            "NGX", "NSE", "STOCK MARKET", "SHARE PRICE", "MARKET CAP",
            "SECTOR", "BANKING", "QUARTERLY RESULTS", "ANNUAL REPORT",
            "CBN", "SEC", "REGULATION"
    );

    private final NewsItemRepository newsItemRepository;

    /**
     * Scrape the latest articles from Nairametrics homepage and business section.
     *
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> scrapeLatestArticles() {
        log.info("Scraping Nairametrics for latest financial news");
        List<NewsItem> allItems = new ArrayList<>();

        try {
            allItems.addAll(scrapeSection(BASE_URL, "homepage"));
        } catch (Exception e) {
            log.error("Failed to scrape Nairametrics homepage: {}", e.getMessage());
        }

        try {
            allItems.addAll(scrapeSection(BUSINESS_URL, "business"));
        } catch (Exception e) {
            log.error("Failed to scrape Nairametrics business section: {}", e.getMessage());
        }

        log.info("Nairametrics scrape complete: {} new articles collected", allItems.size());
        return allItems;
    }

    private List<NewsItem> scrapeSection(String url, String section) {
        log.debug("Scraping Nairametrics {} section: {}", section, url);
        List<NewsItem> items = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .get();

            // Nairametrics uses article cards with various CSS class patterns
            Elements articles = doc.select("article, .post-item, .td-module-container, .entry-title a");

            for (Element article : articles) {
                try {
                    NewsItem item = parseArticleElement(article);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable article element: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error fetching Nairametrics {}: {}", section, e.getMessage());
        }

        return items;
    }

    private NewsItem parseArticleElement(Element article) {
        // Extract title from various possible selectors
        String title = extractTitle(article);
        if (title == null || title.isBlank()) {
            return null;
        }

        // Extract URL
        String articleUrl = extractUrl(article);
        if (articleUrl == null || articleUrl.isBlank()) {
            return null;
        }

        // Deduplicate: skip if we already have this URL
        if (newsItemRepository.existsByUrl(articleUrl)) {
            return null;
        }

        // Extract summary/description
        String summary = extractSummary(article);

        // Extract published date
        LocalDateTime publishedAt = extractPublishedDate(article);

        // Find mentioned stock symbols in title + summary
        String combinedText = title + " " + (summary != null ? summary : "");
        String[] mentionedSymbols = extractMentionedSymbols(combinedText);

        // Calculate relevance score
        int relevanceScore = calculateRelevance(title, summary);

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("Nairametrics")
                .url(truncate(articleUrl, 1000))
                .publishedAt(publishedAt)
                .symbols(mentionedSymbols.length > 0 ? mentionedSymbols : null)
                .relevanceScore(relevanceScore)
                .summary(truncate(summary, 2000))
                .isProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return newsItemRepository.save(newsItem);
    }

    private String extractTitle(Element article) {
        // Try common title selectors used by Nairametrics
        Element titleEl = article.selectFirst(".entry-title a");
        if (titleEl != null) return titleEl.text().trim();

        titleEl = article.selectFirst("h3 a, h2 a, h4 a");
        if (titleEl != null) return titleEl.text().trim();

        titleEl = article.selectFirst(".td-module-title a");
        if (titleEl != null) return titleEl.text().trim();

        // If the element itself is a link with text
        if (article.tagName().equals("a") && !article.text().isBlank()) {
            return article.text().trim();
        }

        return null;
    }

    private String extractUrl(Element article) {
        Element linkEl = article.selectFirst(".entry-title a, h3 a, h2 a, h4 a, .td-module-title a");
        if (linkEl != null) return linkEl.absUrl("href");

        if (article.tagName().equals("a")) {
            return article.absUrl("href");
        }

        linkEl = article.selectFirst("a[href]");
        if (linkEl != null) return linkEl.absUrl("href");

        return null;
    }

    private String extractSummary(Element article) {
        Element descEl = article.selectFirst(".td-excerpt, .entry-summary, .post-excerpt, p");
        if (descEl != null) {
            String text = descEl.text().trim();
            return text.isBlank() ? null : text;
        }
        return null;
    }

    private LocalDateTime extractPublishedDate(Element article) {
        // Try time element with datetime attribute
        Element timeEl = article.selectFirst("time[datetime]");
        if (timeEl != null) {
            return parseIsoDate(timeEl.attr("datetime"));
        }

        // Try date class selectors
        Element dateEl = article.selectFirst(".entry-date, .td-post-date, .post-date");
        if (dateEl != null) {
            return parseFuzzyDate(dateEl.text().trim());
        }

        return null;
    }

    private LocalDateTime parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // WordPress typically outputs ISO 8601: 2026-02-15T10:30:00+01:00
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                log.trace("Could not parse ISO date: {}", dateStr);
                return null;
            }
        }
    }

    private LocalDateTime parseFuzzyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // Try common formats: "February 15, 2026", "Feb 15, 2026"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
            return java.time.LocalDate.parse(dateStr, formatter).atStartOfDay();
        } catch (DateTimeParseException e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
                return java.time.LocalDate.parse(dateStr, formatter).atStartOfDay();
            } catch (DateTimeParseException ex) {
                log.trace("Could not parse fuzzy date: {}", dateStr);
                return null;
            }
        }
    }

    private String[] extractMentionedSymbols(String text) {
        if (text == null) return new String[0];
        String upper = text.toUpperCase();
        return WATCHED_SYMBOLS.stream()
                .filter(upper::contains)
                .toArray(String[]::new);
    }

    private int calculateRelevance(String title, String summary) {
        String combined = ((title != null ? title : "") + " " + (summary != null ? summary : "")).toUpperCase();
        int score = 0;

        for (String keyword : HIGH_IMPACT_KEYWORDS) {
            if (combined.contains(keyword)) score += 20;
        }

        for (String keyword : MEDIUM_IMPACT_KEYWORDS) {
            if (combined.contains(keyword)) score += 10;
        }

        long symbolMentions = WATCHED_SYMBOLS.stream()
                .filter(combined::contains)
                .count();
        score += (int) (symbolMentions * 5);

        return Math.min(score, 100);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
