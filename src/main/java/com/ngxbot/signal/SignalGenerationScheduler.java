package com.ngxbot.signal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngxbot.signal.entity.TradeSignalEntity;
import com.ngxbot.signal.model.TradeSignal;
import com.ngxbot.signal.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled signal generation.
 * Runs at 3:15 PM WAT (after data collection at 3:00 PM).
 * Generates signals from all strategies and persists them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalGenerationScheduler {

    private final CompositeSignalScorer compositeSignalScorer;
    private final TradeSignalRepository tradeSignalRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate signals daily at 3:15 PM WAT (after EODHD data pull at 3:00 PM).
     */
    @Scheduled(cron = "0 15 15 * * MON-FRI", zone = "Africa/Lagos")
    public void generateDailySignals() {
        LocalDate today = LocalDate.now();
        log.info("=== SIGNAL GENERATION STARTED for {} ===", today);

        try {
            List<TradeSignal> signals = compositeSignalScorer.generateSignals(today);

            int persisted = 0;
            for (TradeSignal signal : signals) {
                try {
                    persistSignal(signal);
                    persisted++;
                } catch (Exception e) {
                    log.error("Failed to persist signal for {}: {}", signal.symbol(), e.getMessage());
                }
            }

            log.info("=== SIGNAL GENERATION COMPLETE — {} signals generated, {} persisted ===",
                    signals.size(), persisted);
        } catch (Exception e) {
            log.error("Signal generation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for signal generation.
     */
    public List<TradeSignal> generateSignalsManually(LocalDate date) {
        log.info("Manual signal generation triggered for {}", date);
        List<TradeSignal> signals = compositeSignalScorer.generateSignals(date);
        signals.forEach(this::persistSignal);
        return signals;
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
