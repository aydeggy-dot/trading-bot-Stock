package com.ngxbot.signal;

import com.ngxbot.news.scorer.NewsImpactScorer;
import com.ngxbot.signal.model.SignalStrength;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.strategy.PensionFlowOverlay;
import com.ngxbot.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates signals from all strategies with 4-component weighted scoring.
 * <p>
 * Weight components:
 * <ul>
 *   <li>Tech (40%): RSI, MACD, SMA signals — MOMENTUM_BREAKOUT, US_EARNINGS_MOMENTUM, US_ETF_ROTATION</li>
 *   <li>Fundamental (30%): value, dividend, earnings — DIVIDEND_ACCUMULATION, VALUE_ACCUMULATION, SECTOR_ROTATION</li>
 *   <li>NAV (20%): ETF NAV discount signals — ETF_NAV_ARBITRAGE</li>
 *   <li>News (10%): news impact score bonus — applied to all non-systematic signals</li>
 * </ul>
 * Systematic strategies (DOLLAR_COST_AVERAGING, CURRENCY_HEDGE) skip weighting.
 * <p>
 * Deduplicates signals for the same symbol, preferring higher confidence.
 * Applies PensionFlowOverlay adjustments.
 */
@Slf4j
@Service
public class CompositeSignalScorer {

    private static final double TECH_WEIGHT = 0.40;
    private static final double FUNDAMENTAL_WEIGHT = 0.30;
    private static final double NAV_WEIGHT = 0.20;
    private static final double NEWS_WEIGHT = 0.10;
    private static final int MAX_NEWS_BONUS = 10;
    private static final int MAX_CONFIDENCE = 100;

    private static final Set<String> TECH_STRATEGIES = Set.of(
            "MOMENTUM_BREAKOUT", "US_EARNINGS_MOMENTUM", "US_ETF_ROTATION"
    );
    private static final Set<String> FUNDAMENTAL_STRATEGIES = Set.of(
            "DIVIDEND_ACCUMULATION", "VALUE_ACCUMULATION", "SECTOR_ROTATION"
    );
    private static final Set<String> NAV_STRATEGIES = Set.of(
            "ETF_NAV_ARBITRAGE"
    );
    private static final Set<String> SYSTEMATIC_STRATEGIES = Set.of(
            "DOLLAR_COST_AVERAGING", "CURRENCY_HEDGE"
    );

    private final List<Strategy> strategies;
    private final PensionFlowOverlay pensionFlowOverlay;
    private final NewsImpactScorer newsImpactScorer;

    public CompositeSignalScorer(List<Strategy> strategies,
                                 PensionFlowOverlay pensionFlowOverlay,
                                 NewsImpactScorer newsImpactScorer) {
        // Filter out PensionFlowOverlay from signal-generating strategies
        this.strategies = strategies.stream()
                .filter(s -> !(s instanceof PensionFlowOverlay))
                .toList();
        this.pensionFlowOverlay = pensionFlowOverlay;
        this.newsImpactScorer = newsImpactScorer;
        log.info("CompositeSignalScorer initialized with {} active strategies: {}",
                this.strategies.size(),
                this.strategies.stream().map(Strategy::getName).toList());
    }

    /**
     * Generate and score signals from all enabled strategies for all target symbols.
     *
     * @param date evaluation date
     * @return list of scored and deduplicated trade signals, sorted by confidence descending
     */
    public List<TradeSignal> generateSignals(LocalDate date) {
        log.info("Generating composite signals for {}", date);

        List<TradeSignal> allSignals = new ArrayList<>();

        for (Strategy strategy : strategies) {
            if (!strategy.isEnabled()) {
                log.debug("Strategy {} is disabled — skipping", strategy.getName());
                continue;
            }

            for (String symbol : strategy.getTargetSymbols()) {
                try {
                    List<TradeSignal> signals = strategy.evaluate(symbol, date);
                    allSignals.addAll(signals);
                } catch (Exception e) {
                    log.error("Strategy {} failed for {}: {}", strategy.getName(), symbol, e.getMessage());
                }
            }
        }

        log.info("Raw signals generated: {}", allSignals.size());

        // Apply news bonus to non-systematic signals
        List<TradeSignal> withNewsBonus = allSignals.stream()
                .map(signal -> applyNewsBonus(signal, date))
                .toList();

        // Apply pension flow adjustments
        List<TradeSignal> adjusted = withNewsBonus.stream()
                .map(this::applyPensionFlowAdjustment)
                .toList();

        // Deduplicate: for same symbol+side, keep highest confidence
        List<TradeSignal> deduplicated = deduplicateSignals(adjusted);

        // Sort by confidence descending
        deduplicated.sort(Comparator.comparingInt(TradeSignal::confidenceScore).reversed());

        log.info("Final signals after deduplication: {}", deduplicated.size());
        for (TradeSignal signal : deduplicated) {
            log.info("  {} {} {} @ {} — confidence={}, strategy={}, strength={}",
                    signal.side(), signal.symbol(), signal.strategy(),
                    signal.suggestedPrice(), signal.confidenceScore(),
                    signal.strategy(), signal.strength());
        }

        return deduplicated;
    }

    /**
     * Generate signals for a single symbol across all strategies.
     */
    public List<TradeSignal> generateSignalsForSymbol(String symbol, LocalDate date) {
        List<TradeSignal> signals = new ArrayList<>();

        for (Strategy strategy : strategies) {
            if (!strategy.isEnabled()) continue;
            try {
                signals.addAll(strategy.evaluate(symbol, date));
            } catch (Exception e) {
                log.error("Strategy {} failed for {}: {}", strategy.getName(), symbol, e.getMessage());
            }
        }

        return signals.stream()
                .map(this::applyPensionFlowAdjustment)
                .sorted(Comparator.comparingInt(TradeSignal::confidenceScore).reversed())
                .toList();
    }

    /**
     * Generate signals from a filtered subset of strategies (e.g., NGX-only or US-only).
     *
     * @param date evaluation date
     * @param filteredStrategies subset of strategies to evaluate
     * @return list of scored and deduplicated trade signals, sorted by confidence descending
     */
    public List<TradeSignal> generateSignals(LocalDate date, List<Strategy> filteredStrategies) {
        log.info("Generating composite signals for {} with {} strategies", date, filteredStrategies.size());

        List<TradeSignal> allSignals = new ArrayList<>();

        for (Strategy strategy : filteredStrategies) {
            if (!strategy.isEnabled()) {
                log.debug("Strategy {} is disabled — skipping", strategy.getName());
                continue;
            }

            for (String symbol : strategy.getTargetSymbols()) {
                try {
                    List<TradeSignal> signals = strategy.evaluate(symbol, date);
                    allSignals.addAll(signals);
                } catch (Exception e) {
                    log.error("Strategy {} failed for {}: {}", strategy.getName(), symbol, e.getMessage());
                }
            }
        }

        log.info("Raw signals generated: {}", allSignals.size());

        // Apply news bonus to non-systematic signals
        List<TradeSignal> withNewsBonus = allSignals.stream()
                .map(signal -> applyNewsBonus(signal, date))
                .toList();

        // Apply pension flow adjustments
        List<TradeSignal> adjusted = withNewsBonus.stream()
                .map(this::applyPensionFlowAdjustment)
                .toList();

        // Deduplicate: for same symbol+side, keep highest confidence
        List<TradeSignal> deduplicated = deduplicateSignals(adjusted);

        // Sort by confidence descending
        deduplicated.sort(Comparator.comparingInt(TradeSignal::confidenceScore).reversed());

        log.info("Final signals after deduplication: {}", deduplicated.size());
        for (TradeSignal signal : deduplicated) {
            log.info("  {} {} {} @ {} — confidence={}, strategy={}, strength={}",
                    signal.side(), signal.symbol(), signal.strategy(),
                    signal.suggestedPrice(), signal.confidenceScore(),
                    signal.strategy(), signal.strength());
        }

        return deduplicated;
    }

    /**
     * Applies a news-driven bonus to the signal's confidence score.
     * Systematic strategies (DCA, CURRENCY_HEDGE) are skipped.
     * News bonus = newsScore * NEWS_WEIGHT, capped at MAX_NEWS_BONUS points.
     * Final confidence is capped at MAX_CONFIDENCE (100).
     */
    private TradeSignal applyNewsBonus(TradeSignal signal, LocalDate date) {
        String strategyName = signal.strategy();
        if (SYSTEMATIC_STRATEGIES.contains(strategyName)) {
            return signal;
        }

        try {
            NewsImpactScorer.NewsScore newsScore = newsImpactScorer.calculateNewsScore(signal.symbol(), date);
            int newsBonus = Math.min((int) (newsScore.overallScore() * NEWS_WEIGHT), MAX_NEWS_BONUS);

            if (newsBonus <= 0) {
                return signal;
            }

            int boostedConfidence = Math.min(signal.confidenceScore() + newsBonus, MAX_CONFIDENCE);

            log.debug("{}: news bonus +{} (newsScore={}, sentiment={}), confidence {} → {}",
                    signal.symbol(), newsBonus, newsScore.overallScore(),
                    newsScore.dominantSentiment(), signal.confidenceScore(), boostedConfidence);

            return new TradeSignal(
                    signal.symbol(), signal.side(), signal.suggestedPrice(),
                    signal.stopLoss(), signal.target(), signal.strength(),
                    boostedConfidence, signal.strategy(), signal.reasoning(),
                    signal.indicators(), signal.signalDate()
            );
        } catch (Exception e) {
            log.warn("Failed to calculate news bonus for {}: {}", signal.symbol(), e.getMessage());
            return signal;
        }
    }

    /**
     * @return the full list of strategies (excluding PensionFlowOverlay)
     */
    public List<Strategy> getStrategies() {
        return Collections.unmodifiableList(strategies);
    }

    private TradeSignal applyPensionFlowAdjustment(TradeSignal signal) {
        if (!pensionFlowOverlay.isEnabled()) return signal;

        int adjustedConfidence = pensionFlowOverlay.adjustConfidence(signal);
        if (adjustedConfidence == signal.confidenceScore()) return signal;

        log.debug("{}: confidence adjusted {} → {} by PensionFlowOverlay",
                signal.symbol(), signal.confidenceScore(), adjustedConfidence);

        // Upgrade strength if confidence is high enough
        SignalStrength adjustedStrength = signal.strength();
        if (adjustedConfidence >= 85 && signal.strength() == SignalStrength.BUY) {
            adjustedStrength = SignalStrength.STRONG_BUY;
        }

        return new TradeSignal(
                signal.symbol(), signal.side(), signal.suggestedPrice(),
                signal.stopLoss(), signal.target(), adjustedStrength,
                adjustedConfidence, signal.strategy(), signal.reasoning(),
                signal.indicators(), signal.signalDate()
        );
    }

    private List<TradeSignal> deduplicateSignals(List<TradeSignal> signals) {
        // Group by symbol + side, keep highest confidence for each
        Map<String, TradeSignal> best = new LinkedHashMap<>();

        for (TradeSignal signal : signals) {
            String key = signal.symbol() + ":" + signal.side();
            TradeSignal existing = best.get(key);
            if (existing == null || signal.confidenceScore() > existing.confidenceScore()) {
                best.put(key, signal);
            }
        }

        return new ArrayList<>(best.values());
    }
}
