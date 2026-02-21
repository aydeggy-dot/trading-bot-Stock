package com.ngxbot.ai.scheduler;

import com.ngxbot.ai.analyzer.AiCrossArticleSynthesizer;
import com.ngxbot.ai.analyzer.AiNewsAnalyzer;
import com.ngxbot.ai.client.AiCostTracker;
import com.ngxbot.ai.repository.AiAnalysisRepository;
import com.ngxbot.config.AiProperties;
import com.ngxbot.data.entity.NewsItem;
import com.ngxbot.data.repository.NewsItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AiAnalysisScheduler {

    private static final int MAX_BATCH_SIZE = 10;
    private static final long DELAY_BETWEEN_CALLS_MS = 5000;

    private final AiNewsAnalyzer aiNewsAnalyzer;
    private final AiCrossArticleSynthesizer aiCrossArticleSynthesizer;
    private final NewsItemRepository newsItemRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiCostTracker aiCostTracker;
    private final AiProperties aiProperties;

    public AiAnalysisScheduler(AiNewsAnalyzer aiNewsAnalyzer,
                               AiCrossArticleSynthesizer aiCrossArticleSynthesizer,
                               NewsItemRepository newsItemRepository,
                               AiAnalysisRepository aiAnalysisRepository,
                               AiCostTracker aiCostTracker,
                               AiProperties aiProperties) {
        this.aiNewsAnalyzer = aiNewsAnalyzer;
        this.aiCrossArticleSynthesizer = aiCrossArticleSynthesizer;
        this.newsItemRepository = newsItemRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiCostTracker = aiCostTracker;
        this.aiProperties = aiProperties;
    }

    /**
     * Processes unanalyzed news articles every 15 minutes.
     * Picks up a maximum of 10 articles per batch and adds a 5-second
     * delay between API calls to respect rate limits and manage costs.
     */
    @Scheduled(fixedDelay = 900000, zone = "Africa/Lagos")
    public void processUnanalyzedArticles() {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping unanalyzed articles processing");
            return;
        }

        List<NewsItem> unprocessed = newsItemRepository.findByIsProcessedFalse();
        if (unprocessed.isEmpty()) {
            log.debug("No unprocessed news articles found");
            return;
        }

        // Limit batch size
        List<NewsItem> batch = unprocessed.stream()
                .limit(MAX_BATCH_SIZE)
                .toList();

        log.info("Processing {} unanalyzed articles (out of {} total unprocessed)",
                batch.size(), unprocessed.size());

        int analyzed = 0;
        int skipped = 0;

        for (NewsItem item : batch) {
            // Check budget before each call
            if (!aiCostTracker.canAffordCall()) {
                log.warn("AI budget exceeded, stopping batch processing after {} articles", analyzed);
                break;
            }

            // Skip if already analyzed
            if (aiAnalysisRepository.existsByNewsItemId(item.getId())) {
                log.debug("Article already analyzed, marking as processed: {}", item.getTitle());
                markAsProcessed(item);
                skipped++;
                continue;
            }

            try {
                aiNewsAnalyzer.analyzeArticle(item);
                analyzed++;
            } catch (Exception e) {
                log.warn("Failed to analyze article '{}': {}", item.getTitle(), e.getMessage());
            }

            // Mark as processed regardless of analysis outcome
            markAsProcessed(item);

            // Delay between API calls to respect rate limits
            if (analyzed < batch.size()) {
                sleepBetweenCalls();
            }
        }

        log.info("Batch processing complete: analyzed={}, skipped={}, remaining={}",
                analyzed, skipped, unprocessed.size() - batch.size());
    }

    /**
     * Runs daily at 9 PM WAT on weekdays.
     * For each symbol with 2+ articles today, synthesizes a cross-article view.
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "Africa/Lagos")
    public void crossArticleSynthesis() {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping cross-article synthesis");
            return;
        }

        if (!aiCostTracker.canAffordCall()) {
            log.warn("AI budget exceeded, skipping cross-article synthesis");
            return;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<NewsItem> todaysArticles = newsItemRepository.findByPublishedAtAfterOrderByPublishedAtDesc(startOfDay);

        if (todaysArticles.isEmpty()) {
            log.debug("No articles published today for cross-article synthesis");
            return;
        }

        // Group articles by symbol
        Map<String, List<NewsItem>> articlesBySymbol = groupArticlesBySymbol(todaysArticles);

        int synthesized = 0;
        for (Map.Entry<String, List<NewsItem>> entry : articlesBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<NewsItem> articles = entry.getValue();

            if (articles.size() < 2) {
                continue;
            }

            if (!aiCostTracker.canAffordCall()) {
                log.warn("AI budget exceeded during cross-article synthesis, stopping");
                break;
            }

            try {
                aiCrossArticleSynthesizer.synthesize(symbol, articles);
                synthesized++;
            } catch (Exception e) {
                log.warn("Failed to synthesize articles for symbol {}: {}", symbol, e.getMessage());
            }

            sleepBetweenCalls();
        }

        log.info("Cross-article synthesis complete: {} symbols processed", synthesized);
    }

    private Map<String, List<NewsItem>> groupArticlesBySymbol(List<NewsItem> articles) {
        Map<String, List<NewsItem>> grouped = new HashMap<>();

        for (NewsItem article : articles) {
            if (article.getSymbols() == null) {
                continue;
            }
            for (String symbol : article.getSymbols()) {
                grouped.computeIfAbsent(symbol, k -> new ArrayList<>()).add(article);
            }
        }

        return grouped;
    }

    private void markAsProcessed(NewsItem item) {
        try {
            item.setIsProcessed(true);
            newsItemRepository.save(item);
        } catch (Exception e) {
            log.warn("Failed to mark article as processed: id={}, title={}", item.getId(), item.getTitle());
        }
    }

    private void sleepBetweenCalls() {
        try {
            Thread.sleep(DELAY_BETWEEN_CALLS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep between AI calls interrupted");
        }
    }
}
