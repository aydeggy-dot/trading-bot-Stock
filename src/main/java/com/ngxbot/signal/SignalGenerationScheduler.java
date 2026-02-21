package com.ngxbot.signal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngxbot.signal.entity.TradeSignalEntity;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.repository.TradeSignalRepository;
import com.ngxbot.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Scheduled signal generation with separate NGX and US market schedules.
 * <p>
 * NGX signals: 3:15 PM WAT (after EODHD data pull at 3:00 PM, post-NGX close).
 * US signals: 9:30 PM WAT (after US market close ~9:00 PM WAT / 4:00 PM ET).
 * </p>
 * Generates signals from market-filtered strategies and persists them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalGenerationScheduler {

    /** Strategy names that target the US market. */
    private static final Set<String> US_STRATEGIES = Set.of(
            "US_EARNINGS_MOMENTUM", "US_ETF_ROTATION"
    );

    /** Strategy names that target both markets or are market-agnostic. */
    private static final Set<String> DUAL_MARKET_STRATEGIES = Set.of(
            "DIVIDEND_ACCUMULATION", "DOLLAR_COST_AVERAGING", "CURRENCY_HEDGE"
    );

    private final CompositeSignalScorer compositeSignalScorer;
    private final TradeSignalRepository tradeSignalRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate NGX signals daily at 3:15 PM WAT (after EODHD data pull at 3:00 PM).
     * Includes NGX-only strategies and the NGX side of dual-market strategies.
     */
    @Scheduled(cron = "0 15 15 * * MON-FRI", zone = "Africa/Lagos")
    public void generateNgxSignals() {
        LocalDate today = LocalDate.now();
        log.info("=== NGX SIGNAL GENERATION STARTED for {} ===", today);

        List<Strategy> ngxStrategies = compositeSignalScorer.getStrategies().stream()
                .filter(s -> !US_STRATEGIES.contains(s.getName()))
                .toList();

        generateAndPersist(today, ngxStrategies, "NGX");
    }

    /**
     * Generate US signals daily at 9:30 PM WAT (after US market close ~9:00 PM WAT).
     * Includes US-only strategies and the US side of dual-market strategies.
     */
    @Scheduled(cron = "0 30 21 * * MON-FRI", zone = "Africa/Lagos")
    public void generateUsSignals() {
        LocalDate today = LocalDate.now();
        log.info("=== US SIGNAL GENERATION STARTED for {} ===", today);

        List<Strategy> usStrategies = compositeSignalScorer.getStrategies().stream()
                .filter(s -> US_STRATEGIES.contains(s.getName()) || DUAL_MARKET_STRATEGIES.contains(s.getName()))
                .toList();

        generateAndPersist(today, usStrategies, "US");
    }

    /**
     * Manual trigger for signal generation (all strategies).
     */
    public List<TradeSignal> generateSignalsManually(LocalDate date) {
        log.info("Manual signal generation triggered for {}", date);
        List<TradeSignal> signals = compositeSignalScorer.generateSignals(date);
        signals.forEach(this::persistSignal);
        return signals;
    }

    private void generateAndPersist(LocalDate date, List<Strategy> strategies, String market) {
        try {
            List<TradeSignal> signals = compositeSignalScorer.generateSignals(date, strategies);

            int persisted = 0;
            for (TradeSignal signal : signals) {
                try {
                    persistSignal(signal);
                    persisted++;
                } catch (Exception e) {
                    log.error("Failed to persist signal for {}: {}", signal.symbol(), e.getMessage());
                }
            }

            log.info("=== {} SIGNAL GENERATION COMPLETE — {} signals generated, {} persisted ===",
                    market, signals.size(), persisted);
        } catch (Exception e) {
            log.error("{} signal generation failed: {}", market, e.getMessage(), e);
        }
    }

    private void persistSignal(TradeSignal signal) {
        String indicatorJson = null;
        if (signal.indicators() != null) {
            try {
                indicatorJson = objectMapper.writeValueAsString(signal.indicators());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize indicators for {}: {}", signal.symbol(), e.getMessage());
            }
        }

        TradeSignalEntity entity = TradeSignalEntity.builder()
                .symbol(signal.symbol())
                .signalDate(signal.signalDate())
                .side(signal.side().name())
                .strength(signal.strength().name())
                .strategy(signal.strategy())
                .confidenceScore(signal.confidenceScore())
                .suggestedEntryPrice(signal.suggestedPrice())
                .suggestedStopLoss(signal.stopLoss())
                .suggestedTarget(signal.target())
                .reasoning(signal.reasoning())
                .indicatorSnapshot(indicatorJson)
                .isActedUpon(false)
                .createdAt(LocalDateTime.now())
                .build();

        tradeSignalRepository.save(entity);
        log.debug("Persisted signal: {} {} {} confidence={}",
                signal.side(), signal.symbol(), signal.strategy(), signal.confidenceScore());
    }
}
