package com.ngxbot.news.scraper;

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

/**
 * Parses the Reuters Africa RSS feed for Nigeria/Africa-relevant financial news.
 * Reuters provides authoritative international coverage of African markets, monetary
 * policy, commodities (oil, gas), and macro events that affect the NGX.
 *
 * <p>Uses Jsoup with {@link Parser#xmlParser()} to parse the RSS XML feed directly.
 * Articles are filtered for Nigeria/Africa relevance via keyword matching before persistence.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReutersRssScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 NGXTradingBot/1.0";
    private static final String FEED_URL = "https://www.reuters.com/rssFeed/africaNews";

    private static final Set<String> WATCHED_SYMBOLS = Set.of(
            "ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "FBNH",
            "DANGCEM", "BUACEMENT", "SEPLAT", "ARADEL", "MTNN",
            "STANBICETF30", "VETGRIF30", "MERGROWTH", "MERVALUE",
            "SIAMLETF40", "NEWGOLD",
            // Common name variants
            "ZENITH", "ACCESS", "DANGOTE", "MTN", "NESTLE", "AIRTEL"
    );

    /**
     * Keywords that indicate an article is relevant to Nigeria or the Nigerian market.
     * Articles not matching any of these keywords are excluded.
     */
    private static final Set<String> NIGERIA_RELEVANCE_KEYWORDS = Set.of(
            "NIGERIA", "NIGERIAN", "LAGOS", "ABUJA", "NGX", "NSE",
            "NAIRA", "NGN", "CBN", "CENTRAL BANK OF NIGERIA",
            "NNPC", "DANGOTE", "OIL", "CRUDE", "OPEC",
            "WEST AFRICA", "ECOWAS", "SUB-SAHARAN", "AFRICA STOCK",
            "ZENITH", "GTCO", "ACCESS BANK", "UBA", "FIRST BANK",
            "SEPLAT", "MTNN", "MTN NIGERIA", "AIRTEL AFRICA",
            "CEMENT", "BUACEMENT", "NESTLE NIGERIA"
    );

    private static final Set<String> HIGH_IMPACT_KEYWORDS = Set.of(
            "DIVIDEND", "EARNINGS", "PROFIT", "LOSS", "ACQUISITION",
            "MERGER", "RIGHTS ISSUE", "BONUS", "STOCK SPLIT", "SUSPENSION",
            "DELISTING", "IPO", "OFFER"
    );

    private static final Set<String> MEDIUM_IMPACT_KEYWORDS = Set.of(
            "NGX", "NSE", "STOCK MARKET", "SHARE PRICE", "MARKET CAP",
            "SECTOR", "BANKING", "QUARTERLY RESULTS", "ANNUAL REPORT",
            "CBN", "SEC", "REGULATION", "INTEREST RATE", "INFLATION",
            "MONETARY POLICY", "EXCHANGE RATE"
    );

    private final NewsItemRepository newsItemRepository;

    /**
     * Parse the Reuters Africa RSS feed and persist Nigeria-relevant articles.
     *
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> scrapeLatestArticles() {
        log.info("Parsing Reuters Africa RSS feed: {}", FEED_URL);

        try {
            Document doc = Jsoup.connect(FEED_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .parser(Parser.xmlParser())
                    .get();

            Elements rssItems = doc.select("item");
            List<NewsItem> savedItems = new ArrayList<>();

            for (Element rssItem : rssItems) {
                try {
                    NewsItem item = parseRssItem(rssItem);
                    if (item != null) {
                        savedItems.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable Reuters RSS item: {}", e.getMessage());
                }
            }

            log.info("Reuters RSS scrape complete: {} new Nigeria-relevant articles from {} total items",
                    savedItems.size(), rssItems.size());
            return savedItems;

        } catch (Exception e) {
            log.error("Error fetching Reuters Africa RSS feed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private NewsItem parseRssItem(Element rssItem) {
        String title = getElementText(rssItem, "title");
        String link = getElementText(rssItem, "link");
        String pubDate = getElementText(rssItem, "pubDate");
        String description = getElementText(rssItem, "description");

        if (title.isEmpty() || link.isEmpty()) {
            return null;
        }

        // Filter for Nigeria/Africa relevance
        String combinedText = (title + " " + description).toUpperCase();
        boolean isRelevant = NIGERIA_RELEVANCE_KEYWORDS.stream()
                .anyMatch(combinedText::contains);

        if (!isRelevant) {
            return null;
        }

        // Deduplicate
        if (newsItemRepository.existsByUrl(link)) {
            return null;
        }

        // Extract mentioned symbols
        String[] mentionedSymbols = extractMentionedSymbols(title + " " + description);

        // Calculate relevance
        int relevanceScore = calculateRelevance(title, description);

        // Clean HTML from description
        String cleanSummary = cleanHtml(description);

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("Reuters")
                .url(truncate(link, 1000))
                .publishedAt(parseRssDate(pubDate))
                .symbols(mentionedSymbols.length > 0 ? mentionedSymbols : null)
                .relevanceScore(relevanceScore)
                .summary(truncate(cleanSummary, 2000))
                .isProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return newsItemRepository.save(newsItem);
    }

    private String getElementText(Element parent, String tagName) {
        Element el = parent.selectFirst(tagName);
        return el != null ? el.text().trim() : "";
    }

    /**
     * Parse RSS date format (RFC 822/1123): "Sat, 15 Feb 2026 10:30:00 +0100"
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

    private String[] extractMentionedSymbols(String text) {
        if (text == null) return new String[0];
        String upper = text.toUpperCase();
        return WATCHED_SYMBOLS.stream()
                .filter(upper::contains)
                .toArray(String[]::new);
    }

    private int calculateRelevance(String title, String description) {
        String combined = ((title != null ? title : "") + " " + (description != null ? description : "")).toUpperCase();
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

        // Boost for direct Nigeria mention
        if (combined.contains("NIGERIA") || combined.contains("NIGERIAN")) {
            score += 15;
        }

        return Math.min(score, 100);
    }

    private String cleanHtml(String html) {
        if (html == null || html.isBlank()) return null;
        return Jsoup.parse(html).text();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
