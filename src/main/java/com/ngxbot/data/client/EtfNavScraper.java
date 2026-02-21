package com.ngxbot.data.client;

import com.ngxbot.data.entity.EtfValuation;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.EtfValuationRepository;
import com.ngxbot.data.repository.OhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtfNavScraper {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // Map ETF symbols to their fund manager NAV page URLs
    private static final Map<String, NavSource> ETF_NAV_SOURCES = Map.of(
            "STANBICETF30", new NavSource("https://www.stanbicibtcassetmanagement.com", "Stanbic IBTC"),
            "SIAMLETF40", new NavSource("https://www.stanbicibtcassetmanagement.com", "Stanbic IBTC"),
            "VETGRIF30", new NavSource("https://www.vetiva.com", "Vetiva"),
            "VETINDETF", new NavSource("https://www.vetiva.com", "Vetiva"),
            "MERGROWTH", new NavSource("https://www.meristemwealth.com", "Meristem"),
            "MERVALUE", new NavSource("https://www.meristemwealth.com", "Meristem")
    );

    private final EtfValuationRepository etfValuationRepository;
    private final OhlcvRepository ohlcvRepository;

    /**
     * Scrape NAV data for all tracked ETFs and compute premium/discount.
     * Runs daily at 4:00 PM WAT after fund managers publish NAV.
     */
    public List<EtfValuation> scrapeAllEtfNavs() {
        log.info("Starting ETF NAV scrape for {} ETFs", ETF_NAV_SOURCES.size());
        List<EtfValuation> results = new ArrayList<>();

        for (Map.Entry<String, NavSource> entry : ETF_NAV_SOURCES.entrySet()) {
            try {
                EtfValuation valuation = scrapeEtfNav(entry.getKey(), entry.getValue());
                if (valuation != null) {
                    results.add(valuation);
                }
            } catch (Exception e) {
                log.error("Failed to scrape NAV for {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("Completed ETF NAV scrape: {}/{} successful", results.size(), ETF_NAV_SOURCES.size());
        return results;
    }

    /**
     * Scrape NAV for a single ETF from its fund manager website.
     */
    public EtfValuation scrapeEtfNav(String symbol, NavSource source) {
        log.info("Scraping NAV for {} from {}", symbol, source.url());

        try {
            Document doc = Jsoup.connect(source.url())
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .get();

            BigDecimal nav = extractNavFromPage(doc, symbol, source.name());
            if (nav == null) {
                log.warn("Could not extract NAV for {} from {}", symbol, source.url());
                return null;
            }

            // Get today's market price from OHLCV data
            LocalDate today = LocalDate.now();
            BigDecimal marketPrice = getLatestMarketPrice(symbol, today);
            if (marketPrice == null) {
                log.warn("No market price available for {} on {}", symbol, today);
                return null;
            }

            // Calculate premium/discount percentage: ((market - nav) / nav) * 100
            BigDecimal premiumDiscountPct = marketPrice.subtract(nav)
                    .divide(nav, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            // Upsert valuation
            EtfValuation valuation = etfValuationRepository.findBySymbolAndTradeDate(symbol, today)
                    .orElse(EtfValuation.builder()
                            .symbol(symbol)
                            .tradeDate(today)
                            .createdAt(LocalDateTime.now())
                            .build());

            valuation.setMarketPrice(marketPrice);
            valuation.setNav(nav);
            valuation.setPremiumDiscountPct(premiumDiscountPct);
            valuation.setNavSource(source.name());

            EtfValuation saved = etfValuationRepository.save(valuation);
            log.info("ETF {} — NAV: {}, Market: {}, Premium/Discount: {}%",
                    symbol, nav, marketPrice, premiumDiscountPct);
            return saved;

        } catch (Exception e) {
            log.error("Error scraping NAV for {} from {}: {}", symbol, source.url(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract NAV value from a fund manager's page.
     * Attempts multiple selectors since fund manager websites vary in structure.
     * These selectors are best-effort and will need adjustment when first run against live sites.
     */
    private BigDecimal extractNavFromPage(Document doc, String symbol, String sourceName) {
        // Strategy: search for elements containing the symbol name and nearby numeric values
        // Each fund manager has different page structures

        // Try common patterns for NAV display
        String[] navSelectors = {
                // Look for elements containing "NAV" text
                "*:containsOwn(NAV)",
                "*:containsOwn(Net Asset Value)",
                "*:containsOwn(nav per unit)",
                // Table-based layouts
                "table td:containsOwn(" + symbol + ")",
                // Common CSS class patterns
                ".nav-value",
                ".fund-nav",
                "[data-fund='" + symbol + "']"
        };

        for (String selector : navSelectors) {
            try {
                Elements elements = doc.select(selector);
                for (Element el : elements) {
                    // Check element and its siblings for a numeric value
                    BigDecimal value = findNumericValueNear(el);
                    if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                        log.debug("Found NAV {} for {} using selector: {}", value, symbol, selector);
                        return value;
                    }
                }
            } catch (Exception e) {
                log.trace("Selector {} failed for {}: {}", selector, symbol, e.getMessage());
            }
        }

        // Fallback: scan all text nodes for patterns like "NAV: 123.45" or "N123.45"
        String bodyText = doc.body().text();
        return extractNavFromText(bodyText, symbol);
    }

    /**
     * Try to find a numeric value near the given element (siblings, parent's children).
     */
    private BigDecimal findNumericValueNear(Element element) {
        // Check the element's own text
        BigDecimal value = parseCurrencyValue(element.text());
        if (value != null) return value;

        // Check next sibling
        Element next = element.nextElementSibling();
        if (next != null) {
            value = parseCurrencyValue(next.text());
            if (value != null) return value;
        }

        // Check parent's children
        Element parent = element.parent();
        if (parent != null) {
            for (Element child : parent.children()) {
                value = parseCurrencyValue(child.text());
                if (value != null && value.compareTo(BigDecimal.ONE) > 0) return value;
            }
        }

        return null;
    }

    /**
     * Attempt to extract NAV from raw text using regex patterns.
     */
    private BigDecimal extractNavFromText(String text, String symbol) {
        if (text == null) return null;

        // Look for patterns near the symbol name
        int symbolIdx = text.toUpperCase().indexOf(symbol);
        if (symbolIdx >= 0) {
            // Get a window of text around the symbol
            int start = Math.max(0, symbolIdx - 50);
            int end = Math.min(text.length(), symbolIdx + symbol.length() + 200);
            String window = text.substring(start, end);

            return parseCurrencyValue(window);
        }

        return null;
    }

    /**
     * Parse a currency value from text, handling NGN formatting (e.g., "N1,234.56", "₦1234.56", "1,234.56").
     */
    private BigDecimal parseCurrencyValue(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            // Remove currency symbols, commas, whitespace
            String cleaned = text.replaceAll("[₦N,\\s]", "");
            // Find the first decimal number pattern
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+\\.\\d{2,4})")
                    .matcher(cleaned);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        } catch (Exception e) {
            // ignore parse failures
        }
        return null;
    }

    /**
     * Get the latest market price for a symbol, checking today and yesterday.
     */
    private BigDecimal getLatestMarketPrice(String symbol, LocalDate date) {
        // Try today first
        return ohlcvRepository.findBySymbolAndTradeDate(symbol, date)
                .map(OhlcvBar::getClosePrice)
                .or(() -> ohlcvRepository.findBySymbolAndTradeDate(symbol, date.minusDays(1))
                        .map(OhlcvBar::getClosePrice))
                .or(() -> ohlcvRepository.findBySymbolAndTradeDate(symbol, date.minusDays(2))
                        .map(OhlcvBar::getClosePrice))
                .orElse(null);
    }

    /**
     * Record to hold NAV source info.
     */
    public record NavSource(String url, String name) {}
}
