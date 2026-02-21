package com.ngxbot.data.client;

import com.ngxbot.data.entity.MarketIndex;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.MarketIndexRepository;
import com.ngxbot.data.repository.OhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NgxWebScraper {

    private static final String NGX_PRICE_LIST_URL = "https://ngxgroup.com/exchange/trade/equities/price-list/";
    private static final int TIMEOUT_MS = 30_000;

    private final OhlcvRepository ohlcvRepository;
    private final MarketIndexRepository marketIndexRepository;

    /**
     * Scrape the NGX daily price list page for OHLCV data.
     * This is a BACKUP source — runs after EODHD attempt.
     *
     * @return list of scraped and persisted OhlcvBar entities
     */
    public List<OhlcvBar> scrapeDailyPrices() {
        log.info("Scraping NGX daily price list from {}", NGX_PRICE_LIST_URL);

        try {
            Document doc = Jsoup.connect(NGX_PRICE_LIST_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            Elements rows = doc.select("table tbody tr");
            if (rows.isEmpty()) {
                log.warn("No price data rows found on NGX page — page structure may have changed");
                return Collections.emptyList();
            }

            LocalDate today = LocalDate.now();
            List<OhlcvBar> bars = new ArrayList<>();

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 7) continue;

                    String symbol = cells.get(0).text().trim().toUpperCase();
                    if (symbol.isEmpty()) continue;

                    BigDecimal open = parseBigDecimal(cells.get(1).text());
                    BigDecimal high = parseBigDecimal(cells.get(2).text());
                    BigDecimal low = parseBigDecimal(cells.get(3).text());
                    BigDecimal close = parseBigDecimal(cells.get(4).text());
                    Long volume = parseLong(cells.get(5).text());

                    if (close == null) continue;

                    OhlcvBar bar = ohlcvRepository.findBySymbolAndTradeDate(symbol, today)
                            .orElse(OhlcvBar.builder()
                                    .symbol(symbol)
                                    .tradeDate(today)
                                    .dataSource("NGX_SCRAPE")
                                    .createdAt(LocalDateTime.now())
                                    .build());

                    // Only update if data source is NGX_SCRAPE (don't overwrite EODHD data)
                    if (bar.getId() == null || "NGX_SCRAPE".equals(bar.getDataSource())) {
                        bar.setOpenPrice(open);
                        bar.setHighPrice(high);
                        bar.setLowPrice(low);
                        bar.setClosePrice(close);
                        bar.setAdjustedClose(close);
                        bar.setVolume(volume);
                        bar.setDataSource("NGX_SCRAPE");
                        bars.add(ohlcvRepository.save(bar));
                    }
                } catch (Exception e) {
                    log.debug("Skipping row due to parse error: {}", e.getMessage());
                }
            }

            log.info("Scraped and stored {} price bars from NGX", bars.size());
            return bars;

        } catch (Exception e) {
            log.error("Failed to scrape NGX price list: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Scrape the NGX ASI (All Share Index) value.
     */
    public MarketIndex scrapeAsiIndex() {
        log.info("Scraping NGX ASI index");

        try {
            Document doc = Jsoup.connect(NGX_PRICE_LIST_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            // Look for ASI value in summary section
            Element asiElement = doc.selectFirst(".asi-value, [data-label='ASI'], .index-value");
            if (asiElement == null) {
                log.warn("Could not locate ASI element on NGX page");
                return null;
            }

            BigDecimal asiValue = parseBigDecimal(asiElement.text());
            if (asiValue == null) {
                log.warn("Could not parse ASI value from: {}", asiElement.text());
                return null;
            }

            LocalDate today = LocalDate.now();
            MarketIndex index = marketIndexRepository.findByIndexNameAndTradeDate("ASI", today)
                    .orElse(MarketIndex.builder()
                            .indexName("ASI")
                            .tradeDate(today)
                            .createdAt(LocalDateTime.now())
                            .build());

            index.setCloseValue(asiValue);
            return marketIndexRepository.save(index);

        } catch (Exception e) {
            log.error("Failed to scrape ASI index: {}", e.getMessage(), e);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.replaceAll("[^\\d.\\-]", "");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.replaceAll("[^\\d\\-]", "");
            if (cleaned.isEmpty()) return null;
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
