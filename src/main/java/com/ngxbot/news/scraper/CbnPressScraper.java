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
 * Scrapes the Central Bank of Nigeria (CBN) website for press releases and policy announcements.
 * CBN press releases are HIGH IMPACT for the NGX, especially for banking sector stocks
 * (ZENITHBANK, GTCO, ACCESSCORP, UBA, FBNH). Key areas monitored:
 *
 * <ul>
 *   <li>Monetary policy decisions (MPR changes, CRR adjustments)</li>
 *   <li>Foreign exchange policy (I&E window, bureau de change rules)</li>
 *   <li>Banking regulations (capital requirements, licensing)</li>
 *   <li>Circulars and guidelines affecting financial sector operations</li>
 * </ul>
 *
 * <p>These announcements can cause significant intraday moves in banking stocks and
 * the broader NGX ASI. The relevance scoring gives CBN releases a built-in boost.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CbnPressScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 NGXTradingBot/1.0";
    private static final String CBN_BASE_URL = "https://www.cbn.gov.ng/";
    private static final String CBN_PRESS_URL = "https://www.cbn.gov.ng/Out/Pressrelease.asp";
    private static final String CBN_COMMUNIQUE_URL = "https://www.cbn.gov.ng/MonetaryPolicy/decisions.asp";

    private static final Set<String> WATCHED_SYMBOLS = Set.of(
            "ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "FBNH",
            "DANGCEM", "BUACEMENT", "SEPLAT", "ARADEL", "MTNN",
            "STANBICETF30", "VETGRIF30", "MERGROWTH", "MERVALUE",
            "SIAMLETF40", "NEWGOLD",
            // Common name variants
            "ZENITH", "ACCESS", "DANGOTE", "MTN", "NESTLE", "AIRTEL"
    );

    /**
     * Keywords indicating high-impact CBN policy that directly affects stock prices.
     */
    private static final Set<String> CBN_HIGH_IMPACT_KEYWORDS = Set.of(
            "MONETARY POLICY RATE", "MPR", "INTEREST RATE", "CRR",
            "CASH RESERVE RATIO", "LIQUIDITY RATIO",
            "EXCHANGE RATE", "FOREX", "FX POLICY", "DEVALUATION",
            "NAIRA", "I&E WINDOW", "BDC",
            "CAPITAL REQUIREMENT", "RECAPITALIZATION", "RECAPITALISATION",
            "BANKING LICENSE", "BANKING LICENCE", "REVOCATION",
            "LOAN TO DEPOSIT", "LDR", "OPEN MARKET OPERATIONS", "OMO",
            "TREASURY BILL", "T-BILL", "STANDING LENDING FACILITY"
    );

    private static final Set<String> CBN_MEDIUM_IMPACT_KEYWORDS = Set.of(
            "CIRCULAR", "GUIDELINE", "REGULATION", "DIRECTIVE",
            "COMMUNIQUE", "MPC", "MONETARY POLICY COMMITTEE",
            "FINANCIAL STABILITY", "PAYMENT SYSTEM", "FINTECH",
            "INFLATION", "GDP", "BALANCE OF PAYMENT", "EXTERNAL RESERVE",
            "BANKING SUPERVISION", "DEPOSIT INSURANCE"
    );

    private final NewsItemRepository newsItemRepository;

    /**
     * Scrape the latest CBN press releases and policy announcements.
     * Scrapes both the general press release page and the monetary policy decisions page.
     *
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> scrapeLatestReleases() {
        log.info("Scraping CBN website for press releases and policy announcements");
        List<NewsItem> allItems = new ArrayList<>();

        // Scrape press releases
        try {
            allItems.addAll(scrapePressReleases());
        } catch (Exception e) {
            log.error("Failed to scrape CBN press releases: {}", e.getMessage());
        }

        // Scrape monetary policy decisions/communiques
        try {
            allItems.addAll(scrapeCommuniques());
        } catch (Exception e) {
            log.error("Failed to scrape CBN monetary policy decisions: {}", e.getMessage());
        }

        // Scrape the CBN homepage for featured announcements
        try {
            allItems.addAll(scrapeHomepageAnnouncements());
        } catch (Exception e) {
            log.error("Failed to scrape CBN homepage announcements: {}", e.getMessage());
        }

        log.info("CBN scrape complete: {} new releases collected", allItems.size());
        return allItems;
    }

    private List<NewsItem> scrapePressReleases() {
        log.debug("Scraping CBN press releases page: {}", CBN_PRESS_URL);
        List<NewsItem> items = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(CBN_PRESS_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .get();

            // CBN press release page typically lists press releases in a table or list
            Elements rows = doc.select("table tr, .press-release-item, li a[href*='press'], a[href*='Press']");

            for (Element row : rows) {
                try {
                    NewsItem item = parsePressReleaseRow(row);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable CBN press release row: {}", e.getMessage());
                }
            }

            // Fallback: try to find any links that look like press releases
            if (items.isEmpty()) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String text = link.text().trim();
                    String href = link.absUrl("href");
                    if (text.length() > 20 && isCbnRelevantTitle(text) && !href.isBlank()) {
                        NewsItem item = buildCbnNewsItem(text, href, null);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching CBN press releases: {}", e.getMessage());
        }

        return items;
    }

    private List<NewsItem> scrapeCommuniques() {
        log.debug("Scraping CBN monetary policy decisions page: {}", CBN_COMMUNIQUE_URL);
        List<NewsItem> items = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(CBN_COMMUNIQUE_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .get();

            // Look for communique links and decision summaries
            Elements links = doc.select("a[href*='communique'], a[href*='Communique'], a[href*='mpc'], a[href*='MPC']");

            for (Element link : links) {
                try {
                    String title = link.text().trim();
                    String url = link.absUrl("href");

                    if (title.isBlank() || title.length() < 10 || url.isBlank()) continue;

                    NewsItem item = buildCbnNewsItem(title, url, null);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable CBN communique link: {}", e.getMessage());
                }
            }

            // Also look for standalone text blocks describing policy decisions
            Elements contentBlocks = doc.select("td, .content-area p, .article-body p");
            for (Element block : contentBlocks) {
                String text = block.text().trim();
                if (text.length() > 50 && isCbnRelevantTitle(text)) {
                    Element parentLink = block.selectFirst("a[href]");
                    if (parentLink != null) {
                        String url = parentLink.absUrl("href");
                        NewsItem item = buildCbnNewsItem(
                                truncate(text, 500),
                                url,
                                text
                        );
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching CBN communiques: {}", e.getMessage());
        }

        return items;
    }

    private List<NewsItem> scrapeHomepageAnnouncements() {
        log.debug("Scraping CBN homepage for featured announcements: {}", CBN_BASE_URL);
        List<NewsItem> items = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(CBN_BASE_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .get();

            // CBN homepage features marquee/slider announcements
            Elements announcements = doc.select(
                    ".announcement a, .news-item a, .slider-item a, marquee a, " +
                    ".featured-news a, a[href*='press'], a[href*='circular']"
            );

            for (Element announcement : announcements) {
                try {
                    String title = announcement.text().trim();
                    String url = announcement.absUrl("href");

                    if (title.isBlank() || title.length() < 15 || url.isBlank()) continue;

                    NewsItem item = buildCbnNewsItem(title, url, null);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.trace("Skipping unparseable CBN homepage announcement: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error fetching CBN homepage: {}", e.getMessage());
        }

        return items;
    }

    private NewsItem parsePressReleaseRow(Element row) {
        // Try to extract from table row
        Elements cells = row.select("td");
        Element link = row.selectFirst("a[href]");

        String title;
        String url;
        String dateStr = null;

        if (link != null) {
            title = link.text().trim();
            url = link.absUrl("href");
        } else if (!cells.isEmpty()) {
            // Table row without explicit link
            title = cells.stream()
                    .map(c -> c.text().trim())
                    .filter(t -> t.length() > 20)
                    .findFirst()
                    .orElse(null);
            url = null;
            link = row.selectFirst("a[href]");
            if (link != null) {
                url = link.absUrl("href");
            }
        } else {
            return null;
        }

        if (title == null || title.isBlank() || title.length() < 10) return null;
        if (url == null || url.isBlank()) return null;

        // Try to extract date from row
        if (cells.size() >= 2) {
            dateStr = cells.get(cells.size() - 1).text().trim();
        }

        return buildCbnNewsItem(title, url, dateStr);
    }

    private NewsItem buildCbnNewsItem(String title, String url, String dateOrSummary) {
        if (newsItemRepository.existsByUrl(url)) {
            return null;
        }

        // Determine if the extra string is a date or summary
        LocalDateTime publishedAt = null;
        String summary = null;

        if (dateOrSummary != null) {
            publishedAt = parseCbnDate(dateOrSummary);
            if (publishedAt == null && dateOrSummary.length() > 30) {
                summary = dateOrSummary;
            }
        }

        String combinedText = title + " " + (summary != null ? summary : "");
        String[] mentionedSymbols = extractMentionedSymbols(combinedText);
        int relevanceScore = calculateCbnRelevance(title, summary);

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("CBN")
                .url(truncate(url, 1000))
                .publishedAt(publishedAt)
                .symbols(mentionedSymbols.length > 0 ? mentionedSymbols : null)
                .relevanceScore(relevanceScore)
                .summary(truncate(summary, 2000))
                .isProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return newsItemRepository.save(newsItem);
    }

    /**
     * CBN-specific relevance scoring. CBN releases get an inherent boost because
     * they are always high-impact for Nigerian financial markets.
     */
    private int calculateCbnRelevance(String title, String summary) {
        String combined = ((title != null ? title : "") + " " + (summary != null ? summary : "")).toUpperCase();

        // CBN releases start with a base score of 30 (inherently important)
        int score = 30;

        for (String keyword : CBN_HIGH_IMPACT_KEYWORDS) {
            if (combined.contains(keyword)) score += 15;
        }

        for (String keyword : CBN_MEDIUM_IMPACT_KEYWORDS) {
            if (combined.contains(keyword)) score += 8;
        }

        long symbolMentions = WATCHED_SYMBOLS.stream()
                .filter(combined::contains)
                .count();
        score += (int) (symbolMentions * 5);

        return Math.min(score, 100);
    }

    private boolean isCbnRelevantTitle(String text) {
        String upper = text.toUpperCase();
        return CBN_HIGH_IMPACT_KEYWORDS.stream().anyMatch(upper::contains)
                || CBN_MEDIUM_IMPACT_KEYWORDS.stream().anyMatch(upper::contains);
    }

    private LocalDateTime parseCbnDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        // CBN uses various date formats
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(dateStr.trim(), formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        log.trace("Could not parse CBN date: {}", dateStr);
        return null;
    }

    private String[] extractMentionedSymbols(String text) {
        if (text == null) return new String[0];
        String upper = text.toUpperCase();
        return WATCHED_SYMBOLS.stream()
                .filter(upper::contains)
                .toArray(String[]::new);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
