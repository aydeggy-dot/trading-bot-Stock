package com.ngxbot.discovery.service;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.WatchlistStock;
import com.ngxbot.data.repository.WatchlistStockRepository;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistManager {

    private final WatchlistStockRepository watchlistStockRepository;
    private final DiscoveredStockRepository discoveredStockRepository;
    private final TradingProperties tradingProperties;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Returns all active watchlist symbols: SEED stocks from YAML config + PROMOTED discovered stocks.
     */
    public List<String> getActiveWatchlist() {
        Set<String> symbols = new HashSet<>();

        // Add SEED stocks from YAML configuration
        symbols.addAll(getSeedSymbols());

        // Add PROMOTED discovered stocks
        List<DiscoveredStock> promotedStocks = discoveredStockRepository
                .findByStatus(WatchlistStatus.PROMOTED.name());
        promotedStocks.forEach(stock -> symbols.add(stock.getSymbol()));

        // Also include ACTIVE watchlist stocks from the database
        List<WatchlistStock> activeStocks = watchlistStockRepository
                .findByStatus(WatchlistStatus.ACTIVE.name());
        activeStocks.forEach(stock -> symbols.add(stock.getSymbol()));

        return new ArrayList<>(symbols);
    }

    /**
     * Checks if a symbol is on the active watchlist (SEED, PROMOTED, or ACTIVE).
     */
    public boolean isOnWatchlist(String symbol) {
        if (getSeedSymbols().contains(symbol)) {
            return true;
        }
        return watchlistStockRepository.findBySymbol(symbol)
                .map(stock -> WatchlistStatus.ACTIVE.name().equals(stock.getStatus())
                        || WatchlistStatus.PROMOTED.name().equals(stock.getStatus()))
                .orElse(false);
    }

    /**
     * Adds a stock to the watchlist with PROMOTED status.
     */
    public void addToWatchlist(String symbol, String companyName, String sector) {
        if (isOnWatchlist(symbol)) {
            log.warn("Symbol {} is already on the watchlist, skipping add", symbol);
            return;
        }

        WatchlistStock watchlistStock = watchlistStockRepository.findBySymbol(symbol)
                .map(existing -> {
                    existing.setStatus(WatchlistStatus.PROMOTED.name());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> WatchlistStock.builder()
                        .symbol(symbol)
                        .companyName(companyName)
                        .sector(sector)
                        .status(WatchlistStatus.PROMOTED.name())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());

        watchlistStockRepository.save(watchlistStock);
        log.info("Added {} ({}) to watchlist with PROMOTED status", symbol, companyName);
    }

    /**
     * Removes a stock from the watchlist by setting its status to DEMOTED.
     */
    public void removeFromWatchlist(String symbol) {
        watchlistStockRepository.findBySymbol(symbol).ifPresent(stock -> {
            stock.setStatus(WatchlistStatus.DEMOTED.name());
            stock.setUpdatedAt(LocalDateTime.now());
            watchlistStockRepository.save(stock);
            log.info("Removed {} from watchlist (status set to DEMOTED)", symbol);
        });
    }

    /**
     * Returns symbols currently in OBSERVATION status.
     */
    public List<String> getObservationSymbols() {
        return discoveredStockRepository.findByStatus(WatchlistStatus.OBSERVATION.name())
                .stream()
                .map(DiscoveredStock::getSymbol)
                .toList();
    }

    /**
     * Returns the current number of active watchlist stocks.
     */
    public int getActiveWatchlistSize() {
        return getActiveWatchlist().size();
    }

    /**
     * Checks if there is capacity to add more stocks to the active watchlist.
     */
    public boolean hasCapacity() {
        return getActiveWatchlistSize() < discoveryProperties.getMaxActiveWatchlistSize();
    }

    /**
     * Checks if there are available observation slots.
     */
    public boolean hasObservationSlots() {
        long observationCount = discoveredStockRepository
                .countByStatus(WatchlistStatus.OBSERVATION.name());
        return observationCount < discoveryProperties.getMaxObservationSlots();
    }

    /**
     * Returns the combined set of SEED symbols from YAML configuration.
     * SEED stocks are NEVER demoted.
     */
    public Set<String> getSeedSymbols() {
        Set<String> seeds = new HashSet<>();
        TradingProperties.Watchlist watchlist = tradingProperties.getWatchlist();
        if (watchlist.getEtfs() != null) {
            seeds.addAll(watchlist.getEtfs());
        }
        if (watchlist.getLargeCaps() != null) {
            seeds.addAll(watchlist.getLargeCaps());
        }
        return seeds;
    }
}
