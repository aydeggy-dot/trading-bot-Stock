package com.ngxbot.discovery.service;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.entity.DiscoveryEvent;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.repository.DiscoveryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemotionPolicy {

    private final DiscoveredStockRepository discoveredStockRepository;
    private final DiscoveryEventRepository discoveryEventRepository;
    private final DiscoveryProperties discoveryProperties;
    private final WatchlistManager watchlistManager;
    private final TradingProperties tradingProperties;

    /**
     * Checks whether a PROMOTED stock meets demotion criteria.
     *
     * Criteria (any one triggers demotion):
     * 1. No signals in noSignalDemotionDays
     * 2. Fundamental score dropped below threshold
     * 3. Regulatory action event detected
     *
     * SEED stocks (from TradingProperties.watchlist) are NEVER demoted.
     *
     * @param stock the discovered stock in PROMOTED status
     * @return true if the stock should be demoted
     */
    public boolean checkForDemotion(DiscoveredStock stock) {
        if (!WatchlistStatus.PROMOTED.name().equals(stock.getStatus())) {
            return false;
        }

        // SEED stocks are NEVER demoted
        if (isSeedStock(stock.getSymbol())) {
            log.debug("Stock {} is a SEED stock, exempt from demotion", stock.getSymbol());
            return false;
        }

        // Check 1: No signals in noSignalDemotionDays
        if (stock.getLastSignalDate() != null) {
            long daysSinceLastSignal = ChronoUnit.DAYS.between(stock.getLastSignalDate(), LocalDate.now());
            if (daysSinceLastSignal >= discoveryProperties.getNoSignalDemotionDays()) {
                log.debug("Stock {} has had no signals for {} days (threshold: {})",
                        stock.getSymbol(), daysSinceLastSignal,
                        discoveryProperties.getNoSignalDemotionDays());
                return true;
            }
        } else if (stock.getPromotionDate() != null) {
            // If never had a signal since promotion, check days since promotion
            long daysSincePromotion = ChronoUnit.DAYS.between(stock.getPromotionDate(), LocalDate.now());
            if (daysSincePromotion >= discoveryProperties.getNoSignalDemotionDays()) {
                log.debug("Stock {} has never had a signal since promotion {} days ago",
                        stock.getSymbol(), daysSincePromotion);
                return true;
            }
        }

        // Check 2: Fundamental score below threshold
        if (stock.getFundamentalScore() != null
                && stock.getFundamentalScore().doubleValue() < discoveryProperties.getMinFundamentalScore()) {
            log.debug("Stock {} fundamental score {} below minimum {}",
                    stock.getSymbol(), stock.getFundamentalScore(),
                    discoveryProperties.getMinFundamentalScore());
            return true;
        }

        // Check 3: Regulatory action event
        var events = discoveryEventRepository.findBySymbolOrderByCreatedAtDesc(stock.getSymbol());
        boolean hasRegulatoryAction = events.stream()
                .anyMatch(event -> event.getReason() != null
                        && event.getReason().toLowerCase().contains("regulatory action"));
        if (hasRegulatoryAction) {
            log.debug("Stock {} has regulatory action event, flagged for demotion", stock.getSymbol());
            return true;
        }

        return false;
    }

    /**
     * Demotes a stock from PROMOTED to DEMOTED status.
     * Sets a cooldown period, removes from watchlist, and records the event.
     *
     * @param stock  the discovered stock to demote
     * @param reason the reason for demotion
     */
    public void demote(DiscoveredStock stock, String reason) {
        // Double-check: SEED stocks must NEVER be demoted
        if (isSeedStock(stock.getSymbol())) {
            log.warn("Attempted to demote SEED stock {}. Operation blocked.", stock.getSymbol());
            return;
        }

        String previousStatus = stock.getStatus();

        // Update discovered stock
        stock.setStatus(WatchlistStatus.DEMOTED.name());
        stock.setDemotionDate(LocalDate.now());
        stock.setDemotionReason(reason);
        stock.setCooldownUntil(LocalDate.now().plusDays(discoveryProperties.getDemotionCooldownDays()));
        stock.setUpdatedAt(LocalDateTime.now());
        discoveredStockRepository.save(stock);

        // Remove from watchlist
        watchlistManager.removeFromWatchlist(stock.getSymbol());

        // Record demotion event
        DiscoveryEvent event = DiscoveryEvent.builder()
                .symbol(stock.getSymbol())
                .eventType("DEMOTED")
                .previousStatus(previousStatus)
                .newStatus(WatchlistStatus.DEMOTED.name())
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
        discoveryEventRepository.save(event);

        log.info("DEMOTED stock {} from {} - reason: {}. Cooldown until: {}",
                stock.getSymbol(), previousStatus, reason, stock.getCooldownUntil());
    }

    /**
     * Checks whether a symbol is a SEED stock from the YAML configuration.
     * SEED stocks are protected from demotion.
     */
    private boolean isSeedStock(String symbol) {
        return watchlistManager.getSeedSymbols().contains(symbol);
    }
}
