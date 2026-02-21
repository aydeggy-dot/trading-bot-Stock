package com.ngxbot.news.classifier;

import com.ngxbot.data.entity.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects insider trading activity from news items and NGX bulletin text.
 * Extracts structured {@link InsiderTrade} records from unstructured text
 * using regex-based pattern matching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsiderTradeDetector {

    /**
     * Represents a detected insider trading transaction.
     *
     * @param symbol      the stock ticker symbol
     * @param insiderName name of the insider
     * @param role        role of the insider (e.g., CEO, Director)
     * @param tradeType   BUY or SELL
     * @param quantity    number of shares transacted
     * @param price       price per share
     * @param tradeDate   date of the transaction
     */
    public static record InsiderTrade(
            String symbol,
            String insiderName,
            String role,
            String tradeType,
            BigDecimal quantity,
            BigDecimal price,
            LocalDate tradeDate
    ) {}

    // ---- Pattern library for insider trade detection ----

    /** Matches "director/CEO/etc bought/sold/acquired/disposed ... shares" */
    private static final Pattern DIRECTOR_TRADE_PATTERN = Pattern.compile(
            "(?i)(?:director|CEO|CFO|COO|MD|chairman|board\\s+member|insider|executive)\\s*" +
            "[,:]?\\s*(?:\\w+\\s+){0,5}" +
            "(bought|sold|acquired|disposed(?:\\s+of)?)" +
            "\\s+([\\d,]+(?:\\.\\d+)?)\\s*(?:units?|shares?)",
            Pattern.CASE_INSENSITIVE
    );

    /** Matches Form 29 filings (Nigerian SEC insider disclosure form) */
    private static final Pattern FORM_29_PATTERN = Pattern.compile(
            "(?i)Form\\s*29.*?([A-Z]{2,}(?:\\.[A-Z]+)?)\\s*[,;:\\-]?\\s*" +
            "(?:.*?(bought|sold|acquired|disposed))?",
            Pattern.CASE_INSENSITIVE
    );

    /** Extracts a stock symbol like ZENITHBANK or GTCO */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "\\b([A-Z]{2,15})(?:\\.XNSA)?\\b"
    );

    /** Extracts a name in "Mr./Mrs./Dr. First Last" or "First Last" format */
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:Mr\\.?|Mrs\\.?|Ms\\.?|Dr\\.?|Chief|Alhaji|Alh\\.?)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){1,3})"
    );

    /** Extracts a role like CEO, Director, MD, etc. */
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?i)\\b(CEO|CFO|COO|MD|Managing\\s+Director|Chairman|Director|" +
            "Executive\\s+Director|Non[- ]Executive\\s+Director|Board\\s+Member|" +
            "Company\\s+Secretary)\\b"
    );

    /** Extracts numeric quantities (with possible commas) */
    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "([\\d,]+(?:\\.\\d+)?)\\s*(?:units?|shares?|ordinary\\s+shares?)"
    );

    /** Extracts price patterns like "N1,234.56" or "NGN 1234.56" or "at 12.50" */
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:N|NGN|\\bat\\b)\\s*([\\d,]+\\.\\d{2})"
    );

    /** Extracts dates in common formats */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{4}-\\d{2}-\\d{2}|" +
            "\\d{1,2}\\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}|" +
            "(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4})"
    );

    /**
     * Attempts to detect an insider trade from a {@link NewsItem}.
     * Combines title and summary for analysis.
     *
     * @param item the news item to analyze
     * @return detected insider trade, or empty if none found
     */
    public Optional<InsiderTrade> detectFromNewsItem(NewsItem item) {
        if (item == null) {
            return Optional.empty();
        }

        StringBuilder textBuilder = new StringBuilder();
        if (item.getTitle() != null) {
            textBuilder.append(item.getTitle());
        }
        if (item.getSummary() != null) {
            textBuilder.append(' ').append(item.getSummary());
        }
        String text = textBuilder.toString();

        if (text.isBlank()) {
            return Optional.empty();
        }

        // Check whether the text looks like insider trading activity
        if (!looksLikeInsiderTrade(text)) {
            return Optional.empty();
        }

        InsiderTrade trade = extractTradeDetails(text, item.getSymbols(), item.getPublishedAt() != null
                ? item.getPublishedAt().toLocalDate() : null);

        if (trade != null) {
            log.info("Insider trade detected from news item id={}: symbol={}, insider={}, type={}, qty={}",
                    item.getId(), trade.symbol(), trade.insiderName(), trade.tradeType(), trade.quantity());
            return Optional.of(trade);
        }

        return Optional.empty();
    }

    /**
     * Detects all insider trades from a raw bulletin text block.
     * A single bulletin may contain multiple insider trade disclosures.
     *
     * @param text the bulletin text to analyze
     * @return list of detected insider trades, empty if none found
     */
    public List<InsiderTrade> detectFromBulletinText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<InsiderTrade> results = new ArrayList<>();

        // Split on common bulletin separators to handle multiple entries
        String[] sections = text.split("(?i)(?=Form\\s*29|(?:director|insider).+(?:bought|sold|acquired|disposed))");

        for (String section : sections) {
            if (section.isBlank() || !looksLikeInsiderTrade(section)) {
                continue;
            }

            InsiderTrade trade = extractTradeDetails(section, null, null);
            if (trade != null) {
                results.add(trade);
                log.info("Insider trade detected from bulletin: symbol={}, insider={}, type={}, qty={}",
                        trade.symbol(), trade.insiderName(), trade.tradeType(), trade.quantity());
            }
        }

        return Collections.unmodifiableList(results);
    }

    // ---- internal helpers ----

    private boolean looksLikeInsiderTrade(String text) {
        return DIRECTOR_TRADE_PATTERN.matcher(text).find()
                || FORM_29_PATTERN.matcher(text).find()
                || text.toLowerCase().contains("insider")
                || text.toLowerCase().contains("director dealing");
    }

    private InsiderTrade extractTradeDetails(String text, String[] knownSymbols, LocalDate fallbackDate) {
        String symbol = extractSymbol(text, knownSymbols);
        String insiderName = extractName(text);
        String role = extractRole(text);
        String tradeType = extractTradeType(text);
        BigDecimal quantity = extractQuantity(text);
        BigDecimal price = extractPrice(text);
        LocalDate tradeDate = extractDate(text);

        if (tradeDate == null) {
            tradeDate = fallbackDate;
        }

        // We require at minimum a trade type to consider this a valid detection
        if (tradeType == null) {
            return null;
        }

        return new InsiderTrade(
                symbol != null ? symbol : "UNKNOWN",
                insiderName != null ? insiderName : "Unknown Insider",
                role != null ? role : "Unknown Role",
                tradeType,
                quantity != null ? quantity : BigDecimal.ZERO,
                price != null ? price : BigDecimal.ZERO,
                tradeDate
        );
    }

    private String extractSymbol(String text, String[] knownSymbols) {
        // Prefer known symbols from the news item
        if (knownSymbols != null && knownSymbols.length > 0) {
            return knownSymbols[0];
        }

        Matcher matcher = SYMBOL_PATTERN.matcher(text);
        // Skip common false-positive abbreviations
        Set<String> excluded = Set.of(
                "CEO", "CFO", "COO", "SEC", "CBN", "NGN", "AGM", "EGM",
                "MD", "MR", "MRS", "DR", "THE", "AND", "FOR", "BUY", "SELL",
                "FORM", "XNSA"
        );

        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!excluded.contains(candidate) && candidate.length() >= 3) {
                return candidate;
            }
        }

        return null;
    }

    private String extractName(String text) {
        Matcher matcher = NAME_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractRole(String text) {
        Matcher matcher = ROLE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractTradeType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("bought") || lower.contains("acquired") || lower.contains("purchase")) {
            return "BUY";
        }
        if (lower.contains("sold") || lower.contains("disposed") || lower.contains("disposal")) {
            return "SELL";
        }
        return null;
    }

    private BigDecimal extractQuantity(String text) {
        Matcher matcher = QUANTITY_PATTERN.matcher(text);
        if (matcher.find()) {
            return parseBigDecimal(matcher.group(1));
        }
        return null;
    }

    private BigDecimal extractPrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            return parseBigDecimal(matcher.group(1));
        }
        return null;
    }

    private LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            String dateStr = matcher.group(1).trim();
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(dateStr, formatter);
                } catch (DateTimeParseException ignored) {
                    // Try next formatter
                }
            }
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String raw) {
        try {
            String cleaned = raw.replace(",", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Could not parse number from '{}'", raw);
            return null;
        }
    }
}
