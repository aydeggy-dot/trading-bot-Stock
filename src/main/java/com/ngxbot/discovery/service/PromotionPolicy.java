package com.ngxbot.discovery.service;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.entity.DiscoveryEvent;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.repository.DiscoveryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionPolicy {

    private final DiscoveredStockRepository discoveredStockRepository;
    private final DiscoveryEventRepository discoveryEventRepository;
    private final DiscoveryProperties discoveryProperties;
    private final WatchlistManager watchlistManager;

    /**
     * Checks whether an OBSERVATION stock meets promotion criteria.
     *
     * Criteria:
     * 1. Observation period met (>= observationPeriodDays since observation start)
     * 2. At least 1 buy signal generated (signalCount > 0)
     * 3. No red flag events detected
     * 4. Fundamental score above minimum threshold
     * 5. Watchlist has capacity for new stocks
     *
     * @param stock the discovered stock in OBSERVATION status
     * @return true if the stock meets all promotion criteria
     */
    public boolean checkForPromotion(DiscoveredStock stock) {
        if (!WatchlistStatus.OBSERVATION.name().equals(stock.getStatus())) {
            log.debug("Stock {} is not in OBSERVATION status, skipping promotion check", stock.getSymbol());
            return false;
        }

        // Check observation period
        if (stock.getObservationStartDate() == null) {
            log.warn("Stock {} has no observation start date set", stock.getSymbol());
            return false;
        }

        LocalDate minObservationDate = stock.getObservationStartDate()
                .plusDays(discoveryProperties.getObservationPeriodDays());
        if (LocalDate.now().isBefore(minObservationDate)) {
            log.debug("Stock {} has not completed observation period (started: {}, required until: {})",
                    stock.getSymbol(), stock.getObservationStartDate(), minObservationDate);
            return false;
        }

        // Check for buy signals
        if (stock.getSignalCount() <= 0) {
            log.debug("Stock {} has no buy signals during observation", stock.getSymbol());
            return false;
        }

        // Check fundamental score
        BigDecimal fundamentalScore = stock.getFundamentalScore();
        if (fundamentalScore == null
                || fundamentalScore.doubleValue() < discoveryProperties.getMinFundamentalScore()) {
            log.debug("Stock {} fundamental score {} below minimum {}",
                    stock.getSymbol(),
                    fundamentalScore,
                    discoveryProperties.getMinFundamentalScore());
            return false;
        }

        // Check for red flag events (REGULATORY_ACTION in event history)
        var events = discoveryEventRepository.findBySymbolOrderByCreatedAtDesc(stock.getSymbol());
        boolean hasRedFlag = events.stream()
                .anyMatch(event -> "REGULATORY_ACTION".equals(event.getReason())
                        || (event.getReason() != null && event.getReason().contains("regulatory")));
        if (hasRedFlag) {
            log.debug("Stock {} has red flag events, not eligible for promotion", stock.getSymbol());
            return false;
        }

        // Check watchlist capacity
        if (!watchlistManager.hasCapacity()) {
            log.debug("Watchlist at max capacity ({}), cannot promote {}",
                    discoveryProperties.getMaxActiveWatchlistSize(), stock.getSymbol());
            return false;
        }

        return true;
    }

    /**
     * Promotes a stock from OBSERVATION to PROMOTED status.
     * Creates a WatchlistStock entry via WatchlistManager and records the promotion event.
     *
     * @param stock the discovered stock to promote
     */
    public void promote(DiscoveredStock stock) {
        String previousStatus = stock.getStatus();

        // Update discovered stock status
        stock.setStatus(WatchlistStatus.PROMOTED.name());
        stock.setPromotionDate(LocalDate.now());
        stock.setUpdatedAt(LocalDateTime.now());
        discoveredStockRepository.save(stock);

        // Add to watchlist
        watchlistManager.addToWatchlist(stock.getSymbol(), stock.getCompanyName(), stock.getSector());

        // Record promotion event
        DiscoveryEvent event = DiscoveryEvent.builder()
                .symbol(stock.getSymbol())
                .eventType("PROMOTED")
                .previousStatus(previousStatus)
                .newStatus(WatchlistStatus.PROMOTED.name())
                .reason(String.format("Promoted after observation period. Signals: %d, Score: %s",
                        stock.getSignalCount(), stock.getFundamentalScore()))
                .createdAt(LocalDateTime.now())
                .build();
        discoveryEventRepository.save(event);

        log.info("PROMOTED stock {} ({}) to active watchlist. Signals: {}, Score: {}",
                stock.getSymbol(), stock.getCompanyName(),
                stock.getSignalCount(), stock.getFundamentalScore());
    }
}
