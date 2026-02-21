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
 * Scrapes Seeking Alpha for Nigeria-related investment articles.
 * Seeking Alpha is a very limited source for Nigerian equities, but occasionally
 * publishes analysis on major dual-listed or Nigeria-exposed companies (e.g., MTN,
 * Airtel Africa, Seplat, Dangote group). Also searches for macro articles about
 * Nigeria's economy and commodity markets affecting NGX stocks.
 *
 * <p>This scraper is conservative and expects low article volume. It searches the
 * Seeking Alpha website for Nigerian company tickers and company names.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeekingAlphaScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 NGXTradingBot/1.0";
    private static final String BASE_URL = "https://seekingalpha.com";

    /**
     * Search queries to run against Seeking Alpha to find Nigeria-relevant articles.
     * These cover the major NGX tickers and Nigerian-exposed companies.
     */
    private static final List<String> SEARCH_QUERIES = List.of(
            "Nigeria stock market",
            "NGX exchange",
            "Dangote Cement",
            "MTN Nigeria",
            "Seplat Energy",
            "Airtel Africa",
            "Nigerian banks"
    );

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
     * Scrape Seeking Alpha for Nigeria-related investment articles.
     * Runs searches for Nigerian tickers and company names, then extracts
     * article listings from search result pages.
     *
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> scrapeLatestArticles() {
        log.info("Scraping Seeking Alpha for Nigeria-related articles ({} search queries)", SEARCH_QUERIES.size());
        List<NewsItem> allItems = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (String query : SEARCH_QUERIES) {
            try {
                List<NewsItem> items = searchAndScrape(query, seenUrls);
                allItems.addAll(items);
            } catch (Exception e) {
                log.warn("Seeking Alpha search failed for query '{}': {}", query, e.getMessage());
            }
        }

        log.info("Seeking Alpha scrape complete: {} new articles collected", allItems.size());
        return allItems;
    }

    private List<NewsItem> searchAndScrape(String query, Set<String> seenUrls) {
        String searchUrl = BASE_URL + "/search?q=" + encodeQuery(query) + "&type=article";
        log.debug("Searching Seeking Alpha: {}", searchUrl);
        List<NewsItem> items = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(searchUrl)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get();

            // Seeking Alpha uses data-driven rendering; try common article selectors
            Elements articles = doc.select(
                    "article, [data-test-id='post-list-item'], .media-body, .search-result, li[class*='article']"
            );

            for (Element article : articles) {
                try {
                    NewsItem item = parseArticleElement(article, seenUrls);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable Seeking Alpha article: {}", e.getMessage());
                }
            }

            // If no structured articles found, try extracting links with titles
            if (articles.isEmpty()) {
                Elements links = doc.select("a[href*='/article/'], a[href*='/news/']");
                for (Element link : links) {
                    try {
                        NewsItem item = parseLinkElement(link, seenUrls);
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        log.trace("Skipping unparseable Seeking Alpha link: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching Seeking Alpha search page: {}", e.getMessage());
        }

        return items;
    }

    private NewsItem parseArticleElement(Element article, Set<String> seenUrls) {
        String title = extractTitle(article);
        if (title == null || title.isBlank()) {
            return null;
        }

        String articleUrl = extractUrl(article);
        if (articleUrl == null || articleUrl.isBlank()) {
            return null;
        }

        // Normalize URL
        if (!articleUrl.startsWith("http")) {
            articleUrl = BASE_URL + articleUrl;
        }

        // In-memory dedup for this run + persistent dedup
        if (seenUrls.contains(articleUrl) || newsItemRepository.existsByUrl(articleUrl)) {
            return null;
        }
        seenUrls.add(articleUrl);

        String summary = extractSummary(article);
        LocalDateTime publishedAt = extractPublishedDate(article);

        String combinedText = title + " " + (summary != null ? summary : "");
        String[] mentionedSymbols = extractMentionedSymbols(combinedText);
        int relevanceScore = calculateRelevance(title, summary);

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("SeekingAlpha")
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

    private NewsItem parseLinkElement(Element link, Set<String> seenUrls) {
        String title = link.text().trim();
        if (title.isBlank() || title.length() < 15) {
            return null; // Skip navigational links
        }

        String articleUrl = link.absUrl("href");
        if (articleUrl.isBlank()) {
            articleUrl = BASE_URL + link.attr("href");
        }

        if (seenUrls.contains(articleUrl) || newsItemRepository.existsByUrl(articleUrl)) {
            return null;
        }
        seenUrls.add(articleUrl);

        String[] mentionedSymbols = extractMentionedSymbols(title);
        int relevanceScore = calculateRelevance(title, null);

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("SeekingAlpha")
                .url(truncate(articleUrl, 1000))
                .publishedAt(null)
                .symbols(mentionedSymbols.length > 0 ? mentionedSymbols : null)
                .relevanceScore(relevanceScore)
                .summary(null)
                .isProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return newsItemRepository.save(newsItem);
    }

    private String extractTitle(Element article) {
        Element titleEl = article.selectFirst("h3 a, h2 a, h4 a, a[data-test-id='post-list-item-title']");
        if (titleEl != null) return titleEl.text().trim();

        titleEl = article.selectFirst("a");
        if (titleEl != null && !titleEl.text().isBlank() && titleEl.text().length() > 15) {
            return titleEl.text().trim();
        }

        return null;
    }

    private String extractUrl(Element article) {
        Element linkEl = article.selectFirst("h3 a, h2 a, h4 a, a[data-test-id='post-list-item-title']");
        if (linkEl != null) return linkEl.absUrl("href");

        linkEl = article.selectFirst("a[href]");
        if (linkEl != null) return linkEl.absUrl("href");

        return null;
    }

    private String extractSummary(Element article) {
        Element descEl = article.selectFirst("p, .item-summary, .search-result-summary");
        if (descEl != null) {
            String text = descEl.text().trim();
            return text.isBlank() ? null : text;
        }
        return null;
    }

    private LocalDateTime extractPublishedDate(Element article) {
        Element timeEl = article.selectFirst("time[datetime]");
        if (timeEl != null) {
            return parseIsoDate(timeEl.attr("datetime"));
        }

        Element dateEl = article.selectFirst("span[data-test-id='post-list-date'], .date-text");
        if (dateEl != null) {
            return parseFuzzyDate(dateEl.text().trim());
        }

        return null;
    }

    private LocalDateTime parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM. d, yyyy", Locale.ENGLISH);
            return java.time.LocalDate.parse(dateStr, formatter).atStartOfDay();
        } catch (DateTimeParseException e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
                return java.time.LocalDate.parse(dateStr, formatter).atStartOfDay();
            } catch (DateTimeParseException ex) {
                log.trace("Could not parse Seeking Alpha date: {}", dateStr);
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

    private String encodeQuery(String query) {
        return query.replace(" ", "+");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
