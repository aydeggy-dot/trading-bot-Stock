package com.ngxbot.news.scorer;

import com.ngxbot.common.model.EventType;
import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import com.ngxbot.news.classifier.EventImpactRules;
import com.ngxbot.news.classifier.NewsEventClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates an overall news-driven impact score for a given stock symbol,
 * combining classified events, impact rules, and time-decay weighting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsImpactScorer {

    private static final int DEFAULT_LOOKBACK_DAYS = 3;
    private static final double TODAY_WEIGHT = 1.0;
    private static final double YESTERDAY_WEIGHT = 0.6;
    private static final double TWO_DAYS_AGO_WEIGHT = 0.3;

    private final NewsEventClassifier newsEventClassifier;
    private final EventImpactRules eventImpactRules;
    private final NewsItemRepository newsItemRepository;

    /**
     * Aggregated news impact score for a symbol on a given date.
     *
     * @param overallScore      composite score in [0, 100]
     * @param dominantSentiment the most common sentiment across detected events (POSITIVE, NEGATIVE, NEUTRAL)
     * @param events            all event types detected across recent news
     * @param articleCount       number of news articles analyzed
     */
    public record NewsScore(
            int overallScore,
            String dominantSentiment,
            List<EventType> events,
            int articleCount
    ) {}

    /**
     * Calculates the composite news impact score for a symbol using the last 3 days
     * of news, with time-decay weighting.
     *
     * @param symbol the NGX stock ticker (e.g., "ZENITHBANK")
     * @param date   the reference date (typically today)
     * @return the aggregated news score
     */
    public NewsScore calculateNewsScore(String symbol, LocalDate date) {
        if (symbol == null || symbol.isBlank() || date == null) {
            return new NewsScore(0, "NEUTRAL", List.of(), 0);
        }

        List<NewsItem> recentNews = getRecentNewsForSymbol(symbol, DEFAULT_LOOKBACK_DAYS);

        if (recentNews.isEmpty()) {
            log.debug("No recent news found for symbol={} within {} days of {}", symbol, DEFAULT_LOOKBACK_DAYS, date);
            return new NewsScore(0, "NEUTRAL", List.of(), 0);
        }

        // Classify all articles and collect weighted scores
        double weightedScoreSum = 0.0;
        double totalWeight = 0.0;
        List<EventType> allEvents = new ArrayList<>();
        Map<String, Integer> sentimentCounts = new HashMap<>();
        sentimentCounts.put("POSITIVE", 0);
        sentimentCounts.put("NEGATIVE", 0);
        sentimentCounts.put("NEUTRAL", 0);

        for (NewsItem item : recentNews) {
            List<EventType> classified = newsEventClassifier.classify(item);
            if (classified.isEmpty()) {
                continue;
            }

            allEvents.addAll(classified);

            // Determine time-decay weight based on article age
            double weight = computeTimeWeight(item, date);

            // Calculate impact for this article's events
            int articleImpact = eventImpactRules.calculateImpact(classified);
            weightedScoreSum += articleImpact * weight;
            totalWeight += weight;

            // Tally sentiment
            for (EventType event : classified) {
                eventImpactRules.getImpactRule(event).ifPresent(rule ->
                        sentimentCounts.merge(rule.defaultSentiment(), 1, Integer::sum)
                );
            }
        }

        int overallScore;
        if (totalWeight > 0) {
            overallScore = (int) Math.min(100, Math.round(weightedScoreSum / totalWeight));
        } else {
            overallScore = 0;
        }

        String dominantSentiment = sentimentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEUTRAL");

        List<EventType> dedupedEvents = allEvents.stream()
                .distinct()
                .collect(Collectors.toUnmodifiableList());

        log.info("News score for symbol={} date={}: score={}, sentiment={}, events={}, articles={}",
                symbol, date, overallScore, dominantSentiment, dedupedEvents, recentNews.size());

        return new NewsScore(overallScore, dominantSentiment, dedupedEvents, recentNews.size());
    }

    /**
     * Retrieves recent news items that mention the given symbol within the
     * specified number of days.
     *
     * @param symbol the NGX stock ticker
     * @param days   number of days to look back
     * @return list of matching news items, sorted by published date descending
     */
    public List<NewsItem> getRecentNewsForSymbol(String symbol, int days) {
        LocalDateTime cutoff = LocalDate.now().minusDays(days).atStartOfDay();

        List<NewsItem> allRecent = newsItemRepository.findByPublishedAtAfterOrderByPublishedAtDesc(cutoff);

        return allRecent.stream()
                .filter(item -> containsSymbol(item, symbol))
                .collect(Collectors.toUnmodifiableList());
    }

    // ---- internal helpers ----

    private double computeTimeWeight(NewsItem item, LocalDate referenceDate) {
        if (item.getPublishedAt() == null) {
            return TWO_DAYS_AGO_WEIGHT; // oldest weight as conservative default
        }

        LocalDate articleDate = item.getPublishedAt().toLocalDate();
        long daysBefore = referenceDate.toEpochDay() - articleDate.toEpochDay();

        if (daysBefore <= 0) {
            return TODAY_WEIGHT;
        } else if (daysBefore == 1) {
            return YESTERDAY_WEIGHT;
        } else {
            return TWO_DAYS_AGO_WEIGHT;
        }
    }

    private boolean containsSymbol(NewsItem item, String symbol) {
        if (item.getSymbols() == null) {
            return false;
        }
        for (String s : item.getSymbols()) {
            if (symbol.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
