package com.ngxbot.discovery.service;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.AiProperties;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.discovery.client.EodhdScreenerClient;
import com.ngxbot.discovery.client.EodhdScreenerClient.ScreenerResult;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateEvaluator {

    private final DiscoveredStockRepository discoveredStockRepository;
    private final EodhdScreenerClient eodhdScreenerClient;
    private final DiscoveryProperties discoveryProperties;
    private final AiProperties aiProperties;

    public record EvaluationResult(boolean passed, BigDecimal fundamentalScore, String reason) {}

    /**
     * Evaluates a discovered stock candidate through a 3-stage filter pipeline.
     *
     * Stage 1 (Basic): market cap, average volume, EPS
     * Stage 2 (Fundamental): P/E ratio, revenue growth, debt/equity
     * Stage 3 (Optional AI): placeholder for AI-based assessment
     */
    public EvaluationResult evaluateCandidate(DiscoveredStock stock) {
        log.debug("Evaluating candidate: {}", stock.getSymbol());

        // We need screener data for the stock to evaluate fundamentals.
        // If no screener data is available, we rely on the fundamental score already set.
        BigDecimal score = stock.getFundamentalScore() != null
                ? stock.getFundamentalScore()
                : BigDecimal.ZERO;

        double minScore = discoveryProperties.getMinFundamentalScore();

        if (score.doubleValue() < minScore) {
            return new EvaluationResult(false, score,
                    String.format("Fundamental score %.2f below minimum %.2f",
                            score.doubleValue(), minScore));
        }

        // Stage 3: Optional AI assessment
        if (aiProperties.isEnabled()) {
            boolean aiPassed = performAiAssessment(stock);
            if (!aiPassed) {
                return new EvaluationResult(false, score, "Failed AI assessment");
            }
        }

        return new EvaluationResult(true, score, "Passed all evaluation stages");
    }

    /**
     * Evaluates a batch of screener results through the 3-stage pipeline.
     * Creates DiscoveredStock entities for stocks that pass and sets them to OBSERVATION status.
     */
    public List<DiscoveredStock> evaluateScreenerResults(List<ScreenerResult> results) {
        DiscoveryProperties.Screener screenerConfig = discoveryProperties.getScreener();
        List<DiscoveredStock> qualified = new ArrayList<>();

        for (ScreenerResult result : results) {
            // Skip stocks already discovered
            if (discoveredStockRepository.existsBySymbol(result.symbol())) {
                log.debug("Skipping already discovered stock: {}", result.symbol());
                continue;
            }

            // Stage 1: Basic filters
            if (!passesBasicFilters(result, screenerConfig)) {
                log.debug("Stock {} failed basic filters", result.symbol());
                continue;
            }

            // Stage 2: Fundamental filters
            if (!passesFundamentalFilters(result, screenerConfig)) {
                log.debug("Stock {} failed fundamental filters", result.symbol());
                continue;
            }

            // Calculate fundamental score
            BigDecimal fundamentalScore = calculateFundamentalScore(result, screenerConfig);

            // Stage 3: Optional AI assessment (placeholder - returns true for now)
            if (aiProperties.isEnabled() && !performAiAssessmentForResult(result)) {
                log.debug("Stock {} failed AI assessment", result.symbol());
                continue;
            }

            // Create entity with OBSERVATION status
            DiscoveredStock stock = DiscoveredStock.builder()
                    .symbol(result.symbol())
                    .companyName(result.name())
                    .sector(result.sector())
                    .discoverySource("SCREENER")
                    .discoveryDate(LocalDate.now())
                    .status(WatchlistStatus.OBSERVATION.name())
                    .fundamentalScore(fundamentalScore)
                    .observationStartDate(LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            DiscoveredStock saved = discoveredStockRepository.save(stock);
            qualified.add(saved);
            log.info("New candidate qualified via screener: {} ({}) - score: {}",
                    result.symbol(), result.name(), fundamentalScore);
        }

        log.info("Screener evaluation complete: {}/{} stocks qualified",
                qualified.size(), results.size());
        return qualified;
    }

    /**
     * Stage 1: Basic filters - market cap, average daily volume, EPS.
     */
    private boolean passesBasicFilters(ScreenerResult result, DiscoveryProperties.Screener config) {
        // Market cap check (in millions)
        BigDecimal marketCapMillions = result.marketCap()
                .divide(BigDecimal.valueOf(1_000_000), BigDecimal.ROUND_HALF_UP);
        if (marketCapMillions.doubleValue() < config.getMinMarketCapMillions()) {
            return false;
        }

        // Average daily volume check
        if (result.avgVolume() < config.getMinAvgDailyVolume()) {
            return false;
        }

        // EPS check
        if (result.eps().doubleValue() < config.getMinEps()) {
            return false;
        }

        return true;
    }

    /**
     * Stage 2: Fundamental filters - P/E ratio, revenue growth, debt/equity.
     */
    private boolean passesFundamentalFilters(ScreenerResult result, DiscoveryProperties.Screener config) {
        // P/E ratio check (only if positive, skip for negative P/E)
        if (result.peRatio().compareTo(BigDecimal.ZERO) > 0
                && result.peRatio().doubleValue() > config.getMaxPeRatio()) {
            return false;
        }

        // Revenue growth and debt/equity checks would require additional data
        // For screener-sourced data, we pass these checks if the data is not available
        return true;
    }

    /**
     * Calculates a fundamental score (0-100) based on screener data.
     */
    private BigDecimal calculateFundamentalScore(ScreenerResult result, DiscoveryProperties.Screener config) {
        double score = 50.0; // Base score

        // EPS contribution (up to +15 points)
        if (result.eps().doubleValue() > 0) {
            score += Math.min(15.0, result.eps().doubleValue() * 3);
        }

        // P/E ratio contribution (up to +15 points for low P/E)
        if (result.peRatio().doubleValue() > 0 && result.peRatio().doubleValue() <= config.getMaxPeRatio()) {
            double peScore = (config.getMaxPeRatio() - result.peRatio().doubleValue())
                    / config.getMaxPeRatio() * 15.0;
            score += peScore;
        }

        // Volume contribution (up to +10 points)
        if (result.avgVolume() > config.getMinAvgDailyVolume()) {
            double volumeRatio = (double) result.avgVolume() / config.getMinAvgDailyVolume();
            score += Math.min(10.0, volumeRatio * 2);
        }

        // Market cap contribution (up to +10 points)
        BigDecimal marketCapMillions = result.marketCap()
                .divide(BigDecimal.valueOf(1_000_000), BigDecimal.ROUND_HALF_UP);
        if (marketCapMillions.doubleValue() > config.getMinMarketCapMillions()) {
            double capRatio = marketCapMillions.doubleValue() / config.getMinMarketCapMillions();
            score += Math.min(10.0, capRatio);
        }

        return BigDecimal.valueOf(Math.min(100.0, score));
    }

    /**
     * Stage 3: AI assessment placeholder for DiscoveredStock.
     * Returns true for now - will be implemented with Claude Haiku integration.
     */
    private boolean performAiAssessment(DiscoveredStock stock) {
        // TODO: Implement Claude Haiku assessment for stock candidates
        log.debug("AI assessment placeholder for {} - returning true", stock.getSymbol());
        return true;
    }

    /**
     * Stage 3: AI assessment placeholder for ScreenerResult.
     * Returns true for now - will be implemented with Claude Haiku integration.
     */
    private boolean performAiAssessmentForResult(ScreenerResult result) {
        // TODO: Implement Claude Haiku assessment for screener results
        log.debug("AI assessment placeholder for {} - returning true", result.symbol());
        return true;
    }
}
