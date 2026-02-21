package com.ngxbot.signal.fundamental;

import com.ngxbot.data.entity.WatchlistStock;
import com.ngxbot.data.repository.WatchlistStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Checks if a stock is eligible for pension fund investment (PenCom regulated).
 * Pension-eligible stocks (NGX 30 members) tend to have stronger institutional support
 * and are used by the Pension Flow Overlay strategy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PencomEligibilityChecker {

    private final WatchlistStockRepository watchlistStockRepository;

    /**
     * Check if a stock is in the NGX 30 index (pension-eligible).
     */
    public boolean isPensionEligible(String symbol) {
        return watchlistStockRepository.findBySymbol(symbol)
                .map(WatchlistStock::getIsPensionEligible)
                .orElse(false);
    }

    /**
     * Check if a stock is in the NGX 30 index.
     */
    public boolean isNgx30Member(String symbol) {
        return watchlistStockRepository.findBySymbol(symbol)
                .map(WatchlistStock::getIsNgx30)
                .orElse(false);
    }

    /**
     * Get all NGX 30 members from the watchlist.
     */
    public List<String> getNgx30Symbols() {
        return watchlistStockRepository.findByIsNgx30True().stream()
                .map(WatchlistStock::getSymbol)
                .toList();
    }

    /**
     * Get the sector for a given symbol.
     */
    public String getSector(String symbol) {
        return watchlistStockRepository.findBySymbol(symbol)
                .map(WatchlistStock::getSector)
                .orElse(null);
    }
}
