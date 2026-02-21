package com.ngxbot.discovery.service;

import com.ngxbot.common.model.EventType;
import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.entity.DiscoveryEvent;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.repository.DiscoveryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsDiscoveryListener {

    private final DiscoveredStockRepository discoveredStockRepository;
    private final DiscoveryEventRepository discoveryEventRepository;
    private final WatchlistManager watchlistManager;

    /**
     * High-impact event types that fast-track a stock from CANDIDATE to OBSERVATION.
     */
    private static final Set<EventType> HIGH_IMPACT_EVENTS = Set.of(
            EventType.ACQUISITION_MERGER,
            EventType.EARNINGS_RELEASE,
            EventType.CBN_POLICY
    );

    /**
     * Inner record representing a simplified news item for discovery processing.
     */
    public record NewsItem(String symbol, String companyName, String sector, String headline) {}

    /**
     * Called by the news classifier when a news item has been classified.
     * If the stock is not on the watchlist and not already discovered, creates a CANDIDATE.
     * High-impact events fast-track the stock to OBSERVATION status.
     *
     * @param item   the classified news item
     * @param events the event types detected in the news item
     */
    public void onNewsClassified(NewsItem item, List<EventType> events) {
        if (item.symbol() == null || item.symbol().isBlank()) {
            return;
        }

        // Skip if already on watchlist
        if (watchlistManager.isOnWatchlist(item.symbol())) {
            log.debug("Stock {} already on watchlist, skipping news discovery", item.symbol());
            return;
        }

        // Check if already in discovered stocks
        var existingOpt = discoveredStockRepository.findBySymbol(item.symbol());

        if (existingOpt.isPresent()) {
            DiscoveredStock existing = existingOpt.get();
            // Update signal tracking
            existing.setLastSignalDate(LocalDate.now());
            existing.setSignalCount(existing.getSignalCount() + 1);
            existing.setUpdatedAt(LocalDateTime.now());

            // Check for fast-track from CANDIDATE to OBSERVATION
            if (WatchlistStatus.CANDIDATE.name().equals(existing.getStatus())
                    && containsHighImpactEvent(events)) {
                fastTrackToObservation(existing, events);
            }

            discoveredStockRepository.save(existing);
            log.debug("Updated signal count for discovered stock: {} (count: {})",
                    item.symbol(), existing.getSignalCount());
            return;
        }

        // New stock - create as CANDIDATE
        boolean isHighImpact = containsHighImpactEvent(events);
        String initialStatus = isHighImpact
                ? WatchlistStatus.OBSERVATION.name()
                : WatchlistStatus.CANDIDATE.name();

        DiscoveredStock newStock = DiscoveredStock.builder()
                .symbol(item.symbol())
                .companyName(item.companyName())
                .sector(item.sector())
                .discoverySource("NEWS")
                .discoveryDate(LocalDate.now())
                .status(initialStatus)
                .lastSignalDate(LocalDate.now())
                .signalCount(1)
                .notes("Discovered via news: " + item.headline())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (isHighImpact) {
            newStock.setObservationStartDate(LocalDate.now());
            log.info("High-impact news fast-tracked {} directly to OBSERVATION", item.symbol());
        }

        discoveredStockRepository.save(newStock);

        // Record discovery event
        DiscoveryEvent event = DiscoveryEvent.builder()
                .symbol(item.symbol())
                .eventType("DISCOVERED")
                .previousStatus(null)
                .newStatus(initialStatus)
                .reason("News discovery: " + item.headline())
                .createdAt(LocalDateTime.now())
                .build();
        discoveryEventRepository.save(event);

        log.info("Discovered new stock via news: {} ({}) - status: {}",
                item.symbol(), item.companyName(), initialStatus);
    }

    private boolean containsHighImpactEvent(List<EventType> events) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(HIGH_IMPACT_EVENTS::contains);
    }

    private void fastTrackToObservation(DiscoveredStock stock, List<EventType> events) {
        String previousStatus = stock.getStatus();
        stock.setStatus(WatchlistStatus.OBSERVATION.name());
        stock.setObservationStartDate(LocalDate.now());
        stock.setUpdatedAt(LocalDateTime.now());

        DiscoveryEvent event = DiscoveryEvent.builder()
                .symbol(stock.getSymbol())
                .eventType("OBSERVATION_STARTED")
                .previousStatus(previousStatus)
                .newStatus(WatchlistStatus.OBSERVATION.name())
                .reason("Fast-tracked due to high-impact events: " + events)
                .createdAt(LocalDateTime.now())
                .build();
        discoveryEventRepository.save(event);

        log.info("Fast-tracked {} from {} to OBSERVATION due to high-impact events: {}",
                stock.getSymbol(), previousStatus, events);
    }
}
