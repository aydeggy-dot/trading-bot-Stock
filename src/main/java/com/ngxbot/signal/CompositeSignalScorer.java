package com.ngxbot.signal;

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
 * Aggregates signals from all strategies with weighted scoring.
 * Deduplicates signals for the same symbol, preferring higher confidence.
 * Applies PensionFlowOverlay adjustments.
 */
@Slf4j
@Service
public class CompositeSignalScorer {

    private final List<Strategy> strategies;
    private final PensionFlowOverlay pensionFlowOverlay;

    public CompositeSignalScorer(List<Strategy> strategies, PensionFlowOverlay pensionFlowOverlay) {
        // Filter out PensionFlowOverlay from signal-generating strategies
        this.strategies = strategies.stream()
                .filter(s -> !(s instanceof PensionFlowOverlay))
                .toList();
        this.pensionFlowOverlay = pensionFlowOverlay;
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

        // Apply pension flow adjustments
        List<TradeSignal> adjusted = allSignals.stream()
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
