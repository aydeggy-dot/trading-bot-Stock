package com.ngxbot.news.scraper;

import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses NGX (Nigerian Exchange) daily bulletin PDFs using Apache PDFBox.
 * NGX publishes daily bulletins containing critical market information:
 *
 * <ul>
 *   <li>Corporate actions (dividends, bonus issues, rights issues, stock splits)</li>
 *   <li>Dividend announcements and qualification/payment dates</li>
 *   <li>Trading suspensions and resumptions</li>
 *   <li>New listings and delistings</li>
 *   <li>Company announcements and regulatory filings</li>
 * </ul>
 *
 * <p>The bulletin PDF text is extracted via {@link PDFTextStripper} and then parsed
 * for structured table data using regex patterns. Each distinct corporate action
 * or announcement is saved as a separate {@link NewsItem} with source "NGX_BULLETIN".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NgxBulletinParser {

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private static final Set<String> WATCHED_SYMBOLS = Set.of(
            "ZENITHBANK", "GTCO", "ACCESSCORP", "UBA", "FBNH",
            "DANGCEM", "BUACEMENT", "SEPLAT", "ARADEL", "MTNN",
            "STANBICETF30", "VETGRIF30", "MERGROWTH", "MERVALUE",
            "SIAMLETF40", "NEWGOLD",
            // Common name variants
            "ZENITH", "ACCESS", "DANGOTE", "MTN", "NESTLE", "AIRTEL"
    );

    /**
     * Section headers in NGX bulletins that indicate important content.
     */
    private static final List<String> BULLETIN_SECTIONS = List.of(
            "CORPORATE ACTION",
            "DIVIDEND",
            "BONUS",
            "RIGHTS ISSUE",
            "SUSPENSION",
            "TRADING HALT",
            "NEW LISTING",
            "DELISTING",
            "COMPANY ANNOUNCEMENT",
            "REGULATORY",
            "PRICE LIST",
            "MARKET SUMMARY"
    );

    // Pattern to match dividend announcements: symbol, amount, qualification date, payment date
    private static final Pattern DIVIDEND_PATTERN = Pattern.compile(
            "(?i)(\\b[A-Z]{2,}\\b)\\s+.*?(?:dividend|div\\.?)\\s+.*?" +
            "(?:N|NGN|\\u20A6)?\\s*(\\d+\\.?\\d*)\\s*(?:per\\s+share|kobo|k)?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match suspension/halt notices
    private static final Pattern SUSPENSION_PATTERN = Pattern.compile(
            "(?i)(suspend(?:ed|ion)|halt(?:ed)?|resum(?:ed|ption))\\s+.*?(\\b[A-Z]{2,}\\b)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match date ranges in bulletin text (e.g., "20 February, 2026")
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+(January|February|March|April|May|June|July|August|September|October|November|December)[,]?\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    private final NewsItemRepository newsItemRepository;

    /**
     * Parse a pre-downloaded NGX bulletin PDF from raw byte content.
     *
     * @param pdfContent the raw PDF file bytes
     * @param sourceUrl  the URL from which the PDF was downloaded (for deduplication and reference)
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> parseBulletinPdf(byte[] pdfContent, String sourceUrl) {
        if (pdfContent == null || pdfContent.length == 0) {
            log.warn("Empty PDF content provided for bulletin parsing");
            return Collections.emptyList();
        }

        log.info("Parsing NGX bulletin PDF ({} bytes) from: {}", pdfContent.length, sourceUrl);

        try (PDDocument document = Loader.loadPDF(pdfContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            if (fullText == null || fullText.isBlank()) {
                log.warn("No text extracted from NGX bulletin PDF: {}", sourceUrl);
                return Collections.emptyList();
            }

            log.debug("Extracted {} characters from NGX bulletin PDF", fullText.length());

            List<NewsItem> items = new ArrayList<>();

            // Parse different types of announcements from the bulletin
            items.addAll(parseDividendAnnouncements(fullText, sourceUrl));
            items.addAll(parseSuspensionNotices(fullText, sourceUrl));
            items.addAll(parseCorporateActions(fullText, sourceUrl));
            items.addAll(parseSectionBasedItems(fullText, sourceUrl));

            log.info("NGX bulletin parsing complete: {} items extracted", items.size());
            return items;

        } catch (IOException e) {
            log.error("Failed to parse NGX bulletin PDF from {}: {}", sourceUrl, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Download an NGX bulletin PDF from a URL and parse it.
     *
     * @param pdfUrl the URL of the NGX bulletin PDF to download
     * @return list of newly discovered and persisted NewsItem entities; empty list on failure
     */
    public List<NewsItem> downloadAndParseBulletin(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            log.warn("Empty PDF URL provided for bulletin download");
            return Collections.emptyList();
        }

        log.info("Downloading NGX bulletin PDF from: {}", pdfUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DOWNLOAD_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pdfUrl))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 NGXTradingBot/1.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("Failed to download NGX bulletin (HTTP {}): {}", response.statusCode(), pdfUrl);
                return Collections.emptyList();
            }

            byte[] pdfContent = response.body();
            log.info("Downloaded NGX bulletin: {} bytes from {}", pdfContent.length, pdfUrl);

            return parseBulletinPdf(pdfContent, pdfUrl);

        } catch (Exception e) {
            log.error("Error downloading NGX bulletin from {}: {}", pdfUrl, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse dividend announcements from the bulletin text.
     */
    private List<NewsItem> parseDividendAnnouncements(String text, String sourceUrl) {
        List<NewsItem> items = new ArrayList<>();
        String[] lines = text.split("\\n");

        boolean inDividendSection = false;
        StringBuilder currentBlock = new StringBuilder();

        for (String line : lines) {
            String upperLine = line.toUpperCase().trim();

            if (upperLine.contains("DIVIDEND") && (upperLine.contains("ANNOUNCEMENT") || upperLine.contains("DECLARATION")
                    || upperLine.contains("CORPORATE ACTION") || upperLine.length() < 50)) {
                inDividendSection = true;
                currentBlock = new StringBuilder();
                continue;
            }

            if (inDividendSection) {
                // End of section detection
                if (upperLine.isBlank() && currentBlock.length() > 0) {
                    inDividendSection = false;
                    continue;
                }
                if (BULLETIN_SECTIONS.stream().anyMatch(s -> upperLine.startsWith(s) && !upperLine.contains("DIVIDEND"))) {
                    inDividendSection = false;
                    continue;
                }

                currentBlock.append(line).append(" ");

                // Try to extract dividend info from accumulated text
                Matcher matcher = DIVIDEND_PATTERN.matcher(currentBlock.toString());
                if (matcher.find()) {
                    String symbol = matcher.group(1);
                    String amount = matcher.group(2);

                    if (isWatchedSymbol(symbol)) {
                        String title = String.format("Dividend: %s declares N%s per share", symbol, amount);
                        String itemUrl = sourceUrl + "#dividend-" + symbol.toLowerCase();

                        NewsItem item = buildBulletinItem(
                                title, itemUrl, currentBlock.toString().trim(),
                                new String[]{symbol}, 85
                        );
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    currentBlock = new StringBuilder();
                }
            }
        }

        // Also do a full-text regex scan for dividend patterns outside sections
        Matcher globalMatcher = DIVIDEND_PATTERN.matcher(text);
        while (globalMatcher.find()) {
            String symbol = globalMatcher.group(1);
            if (isWatchedSymbol(symbol)) {
                String amount = globalMatcher.group(2);
                String title = String.format("Dividend: %s - N%s per share", symbol, amount);
                String itemUrl = sourceUrl + "#dividend-" + symbol.toLowerCase();

                // Only add if not already captured
                if (!newsItemRepository.existsByUrl(itemUrl)) {
                    int contextStart = Math.max(0, globalMatcher.start() - 100);
                    int contextEnd = Math.min(text.length(), globalMatcher.end() + 100);
                    String context = text.substring(contextStart, contextEnd).trim();

                    NewsItem item = buildBulletinItem(title, itemUrl, context, new String[]{symbol}, 85);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        return items;
    }

    /**
     * Parse trading suspension and halt notices from the bulletin text.
     */
    private List<NewsItem> parseSuspensionNotices(String text, String sourceUrl) {
        List<NewsItem> items = new ArrayList<>();

        Matcher matcher = SUSPENSION_PATTERN.matcher(text);
        while (matcher.find()) {
            String action = matcher.group(1).toUpperCase();
            String symbol = matcher.group(2);

            if (isWatchedSymbol(symbol)) {
                String actionType = action.contains("SUSPEND") || action.contains("HALT")
                        ? "Suspension" : "Resumption";
                String title = String.format("Trading %s: %s", actionType, symbol);
                String itemUrl = sourceUrl + "#suspension-" + symbol.toLowerCase();

                int contextStart = Math.max(0, matcher.start() - 150);
                int contextEnd = Math.min(text.length(), matcher.end() + 150);
                String context = text.substring(contextStart, contextEnd).trim();

                // Suspensions are very high impact
                int relevance = action.contains("SUSPEND") || action.contains("HALT") ? 95 : 70;

                NewsItem item = buildBulletinItem(title, itemUrl, context, new String[]{symbol}, relevance);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    /**
     * Parse general corporate actions (bonus issues, rights issues, stock splits, etc.).
     */
    private List<NewsItem> parseCorporateActions(String text, String sourceUrl) {
        List<NewsItem> items = new ArrayList<>();
        String upper = text.toUpperCase();

        Map<String, String> actionPatterns = Map.of(
                "BONUS ISSUE", "Bonus Issue",
                "RIGHTS ISSUE", "Rights Issue",
                "STOCK SPLIT", "Stock Split",
                "SHARE CONSOLIDATION", "Share Consolidation",
                "NEW LISTING", "New Listing",
                "DELISTING", "Delisting"
        );

        for (Map.Entry<String, String> entry : actionPatterns.entrySet()) {
            String keyword = entry.getKey();
            String actionLabel = entry.getValue();
            int idx = 0;

            while ((idx = upper.indexOf(keyword, idx)) != -1) {
                // Extract context around the keyword
                int contextStart = Math.max(0, idx - 200);
                int contextEnd = Math.min(text.length(), idx + keyword.length() + 200);
                String context = text.substring(contextStart, contextEnd).trim();

                // Look for watched symbols in the context
                String[] foundSymbols = extractMentionedSymbols(context);

                if (foundSymbols.length > 0) {
                    String symbolList = String.join(", ", foundSymbols);
                    String title = String.format("%s: %s", actionLabel, symbolList);
                    String itemUrl = sourceUrl + "#action-" + keyword.toLowerCase().replace(" ", "-")
                            + "-" + foundSymbols[0].toLowerCase();

                    int relevance = keyword.contains("DELIST") || keyword.contains("SUSPEND") ? 90 : 75;

                    NewsItem item = buildBulletinItem(title, itemUrl, context, foundSymbols, relevance);
                    if (item != null) {
                        items.add(item);
                    }
                }

                idx += keyword.length();
            }
        }

        return items;
    }

    /**
     * Parse section-based items from the bulletin. Identifies major section headers
     * and extracts content blocks relevant to watched symbols.
     */
    private List<NewsItem> parseSectionBasedItems(String text, String sourceUrl) {
        List<NewsItem> items = new ArrayList<>();
        String[] lines = text.split("\\n");

        String currentSection = null;
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            String upperLine = line.toUpperCase().trim();

            // Detect section headers
            String detectedSection = BULLETIN_SECTIONS.stream()
                    .filter(upperLine::startsWith)
                    .findFirst()
                    .orElse(null);

            if (detectedSection != null) {
                // Process previous section
                if (currentSection != null && sectionContent.length() > 0) {
                    processSection(currentSection, sectionContent.toString(), sourceUrl, items);
                }
                currentSection = detectedSection;
                sectionContent = new StringBuilder();
            } else if (currentSection != null) {
                sectionContent.append(line).append("\n");
            }
        }

        // Process last section
        if (currentSection != null && sectionContent.length() > 0) {
            processSection(currentSection, sectionContent.toString(), sourceUrl, items);
        }

        return items;
    }

    private void processSection(String sectionName, String content, String sourceUrl, List<NewsItem> items) {
        String[] foundSymbols = extractMentionedSymbols(content);
        if (foundSymbols.length == 0) {
            return; // No watched symbols in this section
        }

        for (String symbol : foundSymbols) {
            String title = String.format("NGX Bulletin - %s: %s", sectionName, symbol);
            String itemUrl = sourceUrl + "#section-" + sectionName.toLowerCase().replace(" ", "-")
                    + "-" + symbol.toLowerCase();

            int relevance = calculateBulletinRelevance(sectionName, content);

            NewsItem item = buildBulletinItem(
                    title, itemUrl, truncate(content.trim(), 2000),
                    new String[]{symbol}, relevance
            );
            if (item != null) {
                items.add(item);
            }
        }
    }

    private NewsItem buildBulletinItem(String title, String url, String summary,
                                       String[] symbols, int relevanceScore) {
        if (newsItemRepository.existsByUrl(url)) {
            return null;
        }

        NewsItem newsItem = NewsItem.builder()
                .title(truncate(title, 500))
                .source("NGX_BULLETIN")
                .url(truncate(url, 1000))
                .publishedAt(LocalDateTime.now())
                .symbols(symbols != null && symbols.length > 0 ? symbols : null)
                .relevanceScore(Math.min(relevanceScore, 100))
                .summary(truncate(summary, 2000))
                .isProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return newsItemRepository.save(newsItem);
    }

    private int calculateBulletinRelevance(String sectionName, String content) {
        int score = 40; // Base score for bulletin items (always somewhat relevant)

        if (sectionName.contains("DIVIDEND")) score += 30;
        if (sectionName.contains("SUSPENSION") || sectionName.contains("HALT")) score += 35;
        if (sectionName.contains("DELISTING")) score += 35;
        if (sectionName.contains("RIGHTS ISSUE")) score += 25;
        if (sectionName.contains("BONUS")) score += 25;
        if (sectionName.contains("NEW LISTING")) score += 20;
        if (sectionName.contains("CORPORATE ACTION")) score += 20;

        String upper = content.toUpperCase();
        long symbolCount = WATCHED_SYMBOLS.stream().filter(upper::contains).count();
        score += (int) (symbolCount * 3);

        return Math.min(score, 100);
    }

    private boolean isWatchedSymbol(String symbol) {
        if (symbol == null) return false;
        return WATCHED_SYMBOLS.contains(symbol.toUpperCase());
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
