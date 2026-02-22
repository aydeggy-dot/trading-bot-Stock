package com.ngxbot.dashboard.controller;

import com.ngxbot.ai.client.AiCostTracker;
import com.ngxbot.ai.entity.AiAnalysis;
import com.ngxbot.ai.repository.AiAnalysisRepository;
import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import com.ngxbot.signal.entity.TradeSignalEntity;
import com.ngxbot.signal.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for trade signals, news feed, and AI analysis dashboard.
 * Provides endpoints consumed by the dashboard UI to display signal activity,
 * recent market news, and AI cost/usage metrics.
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class SignalController {

    private static final String XNSA_SUFFIX = ".XNSA";

    private final TradeSignalRepository tradeSignalRepository;
    private final NewsItemRepository newsItemRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiCostTracker aiCostTracker;

    /**
     * Returns today's trade signals, optionally filtered by market.
     * <p>
     * Market filtering uses the EODHD ticker convention:
     * symbols ending with {@code .XNSA} belong to the NGX market;
     * all other symbols are treated as US market.
     *
     * @param market optional filter: "NGX" or "US"
     * @return list of trade signals for today
     */
    @GetMapping("/signals")
    public ResponseEntity<List<TradeSignalEntity>> getTodaySignals(
            @RequestParam(value = "market", required = false) String market) {

        LocalDate today = LocalDate.now();
        List<TradeSignalEntity> signals = tradeSignalRepository
                .findBySignalDateOrderByConfidenceScoreDesc(today);

        if (market != null && !market.isBlank()) {
            signals = filterByMarket(signals, market.trim().toUpperCase());
        }

        log.debug("[SIGNALS] Returning {} signals for {} (market={})",
                signals.size(), today, market);
        return ResponseEntity.ok(signals);
    }

    /**
     * Returns news items published within the last 7 days, ordered by most recent first.
     */
    @GetMapping("/news")
    public ResponseEntity<List<NewsItem>> getRecentNews() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<NewsItem> news = newsItemRepository
                .findByPublishedAtAfterOrderByPublishedAtDesc(sevenDaysAgo);
        log.debug("[NEWS] Returning {} news items from the last 7 days", news.size());
        return ResponseEntity.ok(news);
    }

    /**
     * Returns AI cost tracking summary including today's cost, this month's cost,
     * and whether the budget has been exceeded.
     */
    @GetMapping("/ai/cost")
    public ResponseEntity<Map<String, Object>> getAiCostSummary() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        BigDecimal dailyCost = aiCostTracker.getDailyCost(today);
        BigDecimal monthlyCost = aiCostTracker.getMonthlyCost(currentMonth);
        boolean budgetExceeded = aiCostTracker.isBudgetExceeded();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today);
        result.put("dailyCostUsd", dailyCost);
        result.put("month", currentMonth.toString());
        result.put("monthlyCostUsd", monthlyCost);
        result.put("budgetExceeded", budgetExceeded);

        log.debug("[AI-COST] Daily=${}, Monthly=${}, budgetExceeded={}",
                dailyCost, monthlyCost, budgetExceeded);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns AI analysis records for a given stock symbol from the last 30 days.
     *
     * @param symbol the stock ticker (e.g., "ZENITHBANK" or "ZENITHBANK.XNSA")
     * @return list of AI analyses for the symbol
     */
    @GetMapping("/ai/analysis/{symbol}")
    public ResponseEntity<List<AiAnalysis>> getAiAnalysisForSymbol(
            @PathVariable("symbol") String symbol) {

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AiAnalysis> analyses = aiAnalysisRepository
                .findBySymbolAndCreatedAtAfter(symbol, thirtyDaysAgo);
        log.debug("[AI-ANALYSIS] Returning {} analyses for symbol={}", analyses.size(), symbol);
        return ResponseEntity.ok(analyses);
    }

    /**
     * Returns a placeholder AI fallback rate metric.
     * This tracks how often the bot falls back from Sonnet to Haiku
     * (or skips AI entirely) due to budget constraints or API errors.
     */
    @GetMapping("/ai/fallback-rate")
    public ResponseEntity<Map<String, Object>> getAiFallbackRate() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("info", "AI fallback rate tracking — not yet implemented");
        result.put("fallbackRatePct", 0);
        result.put("totalCalls", 0);
        result.put("fallbackCalls", 0);
        result.put("note", "Will track Sonnet->Haiku downgrades and AI skip events");
        return ResponseEntity.ok(result);
    }

    // ---- Helper methods ----

    /**
     * Filters signals by market using the EODHD ticker convention.
     * Symbols ending with .XNSA are NGX; all others are US.
     */
    private List<TradeSignalEntity> filterByMarket(List<TradeSignalEntity> signals, String market) {
        return signals.stream()
                .filter(signal -> {
                    boolean isNgx = signal.getSymbol() != null
                            && signal.getSymbol().toUpperCase().endsWith(XNSA_SUFFIX);
                    return "NGX".equals(market) ? isNgx : !isNgx;
                })
                .toList();
    }
}
