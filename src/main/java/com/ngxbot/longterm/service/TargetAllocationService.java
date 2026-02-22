package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages target allocations for the core long-term portfolio.
 * <p>
 * Provides methods to read, update, and validate the target weight percentages
 * for each holding. Target weights should sum to approximately 100% across
 * all holdings (a tolerance of 1% is allowed to accommodate rounding).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetAllocationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("1.0");

    private final CoreHoldingRepository coreHoldingRepository;
    private final LongtermProperties longtermProperties;

    /**
     * Returns the target allocations from configuration.
     * <p>
     * The map keys use the format "SYMBOL:MARKET" (e.g., "ZENITHBANK:NGX") and the
     * values are the target weight percentages.
     *
     * @return map of symbol:market to target weight percentage
     */
    public Map<String, BigDecimal> getTargetAllocations() {
        Map<String, BigDecimal> configAllocations = longtermProperties.getCore().getTargetAllocations();

        if (configAllocations != null && !configAllocations.isEmpty()) {
            log.debug("Returning {} target allocations from config", configAllocations.size());
            return new LinkedHashMap<>(configAllocations);
        }

        // Fall back to database holdings if config is empty
        List<CoreHolding> holdings = coreHoldingRepository.findAllByOrderByMarketAscSymbolAsc();
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();

        for (CoreHolding holding : holdings) {
            String key = holding.getSymbol() + ":" + holding.getMarket();
            allocations.put(key, holding.getTargetWeightPct());
        }

        log.debug("Returning {} target allocations from database", allocations.size());
        return allocations;
    }

    /**
     * Updates the target allocation for a single holding identified by symbol and market.
     * <p>
     * If the holding does not exist in the database, a new CoreHolding record is created.
     * The target weight is persisted to the database (not to the YAML config).
     *
     * @param symbol    the stock symbol
     * @param market    the market ("NGX" or "US")
     * @param targetPct the new target weight percentage
     * @throws IllegalArgumentException if targetPct is negative or exceeds 100
     */
    @Transactional
    public void setTargetAllocation(String symbol, String market, BigDecimal targetPct) {
        if (targetPct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Target weight cannot be negative. Got: " + targetPct + " for " + symbol);
        }
        if (targetPct.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "Target weight cannot exceed 100%. Got: " + targetPct + " for " + symbol);
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedMarket = market.trim().toUpperCase();
        String currency = "NGX".equals(normalizedMarket) ? "NGN" : "USD";

        CoreHolding holding = coreHoldingRepository
                .findBySymbolAndMarket(normalizedSymbol, normalizedMarket)
                .map(existing -> {
                    BigDecimal oldTarget = existing.getTargetWeightPct();
                    existing.setTargetWeightPct(targetPct);
                    existing.setUpdatedAt(LocalDateTime.now());
                    log.info("Updated target allocation for {}:{} from {}% to {}%",
                            normalizedSymbol, normalizedMarket, oldTarget, targetPct);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("Creating new core holding for {}:{} with target {}%",
                            normalizedSymbol, normalizedMarket, targetPct);
                    return CoreHolding.builder()
                            .symbol(normalizedSymbol)
                            .market(normalizedMarket)
                            .currency(currency)
                            .targetWeightPct(targetPct)
                            .currentWeightPct(BigDecimal.ZERO)
                            .marketValue(BigDecimal.ZERO)
                            .sharesHeld(0)
                            .avgCostBasis(BigDecimal.ZERO)
                            .build();
                });

        coreHoldingRepository.save(holding);
    }

    /**
     * Validates that all target weights across all core holdings sum to approximately 100%.
     * <p>
     * A tolerance of 1.0 percentage point is allowed to accommodate rounding.
     * Returns true if valid, false otherwise.
     *
     * @return true if allocations sum to approximately 100%, false otherwise
     */
    public boolean validateAllocations() {
        List<CoreHolding> allHoldings = coreHoldingRepository.findAll();

        if (allHoldings.isEmpty()) {
            log.warn("No core holdings found. Validation skipped.");
            return false;
        }

        BigDecimal totalWeight = allHoldings.stream()
                .map(h -> h.getTargetWeightPct() != null ? h.getTargetWeightPct() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deviation = totalWeight.subtract(ONE_HUNDRED).abs();
        boolean isValid = deviation.compareTo(TOLERANCE) <= 0;

        if (isValid) {
            log.info("Target allocations are valid. Total weight: {}% (deviation: {}%)",
                    totalWeight.setScale(2, RoundingMode.HALF_UP),
                    deviation.setScale(2, RoundingMode.HALF_UP));
        } else {
            log.warn("Target allocations are INVALID. Total weight: {}% (deviation: {}%, tolerance: {}%)",
                    totalWeight.setScale(2, RoundingMode.HALF_UP),
                    deviation.setScale(2, RoundingMode.HALF_UP),
                    TOLERANCE);

            // Log individual allocations for debugging
            for (CoreHolding holding : allHoldings) {
                log.warn("  {}:{} = {}%", holding.getSymbol(), holding.getMarket(),
                        holding.getTargetWeightPct());
            }
        }

        return isValid;
    }

    /**
     * Returns the sum of target weights for all core holdings in a specific market.
     *
     * @param market the market identifier ("NGX" or "US")
     * @return total target weight percentage for the market
     */
    public BigDecimal getMarketAllocation(String market) {
        List<CoreHolding> holdings = coreHoldingRepository.findByMarket(market);

        BigDecimal total = holdings.stream()
                .map(h -> h.getTargetWeightPct() != null ? h.getTargetWeightPct() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Total target allocation for market {}: {}%", market, total);
        return total;
    }
}
