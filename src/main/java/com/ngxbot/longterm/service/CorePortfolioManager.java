package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the core holdings list for the long-term portfolio.
 * <p>
 * Responsible for initializing holdings from configuration, tracking market values,
 * computing portfolio weights, and producing drift reports for rebalancing decisions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorePortfolioManager {

    private final CoreHoldingRepository coreHoldingRepository;
    private final PositionRepository positionRepository;
    private final LongtermProperties longtermProperties;

    /**
     * Reads target allocations from {@link LongtermProperties} and upserts CoreHolding records.
     * <p>
     * For each entry in the target allocations map, the symbol format is expected to be
     * "SYMBOL:MARKET" (e.g., "ZENITHBANK:NGX" or "VTI:US"). If no market suffix is provided,
     * NGX is assumed. Currency is derived from market: NGX -> NGN, US -> USD.
     */
    @Transactional
    public void initializeFromConfig() {
        Map<String, BigDecimal> targetAllocations = longtermProperties.getCore().getTargetAllocations();

        if (targetAllocations == null || targetAllocations.isEmpty()) {
            log.warn("No target allocations configured in longterm.core.targetAllocations. Skipping initialization.");
            return;
        }

        log.info("Initializing core holdings from config with {} allocations", targetAllocations.size());

        for (Map.Entry<String, BigDecimal> entry : targetAllocations.entrySet()) {
            String key = entry.getKey();
            BigDecimal targetWeight = entry.getValue();

            String symbol;
            String market;
            if (key.contains(":")) {
                String[] parts = key.split(":", 2);
                symbol = parts[0].trim().toUpperCase();
                market = parts[1].trim().toUpperCase();
            } else {
                symbol = key.trim().toUpperCase();
                market = "NGX";
            }

            String currency = "NGX".equals(market) ? "NGN" : "USD";

            CoreHolding holding = coreHoldingRepository.findBySymbolAndMarket(symbol, market)
                    .map(existing -> {
                        existing.setTargetWeightPct(targetWeight);
                        existing.setUpdatedAt(LocalDateTime.now());
                        log.info("Updated target weight for {}:{} to {}%", symbol, market, targetWeight);
                        return existing;
                    })
                    .orElseGet(() -> {
                        log.info("Creating new core holding {}:{} with target weight {}%", symbol, market, targetWeight);
                        return CoreHolding.builder()
                                .symbol(symbol)
                                .market(market)
                                .currency(currency)
                                .targetWeightPct(targetWeight)
                                .currentWeightPct(BigDecimal.ZERO)
                                .marketValue(BigDecimal.ZERO)
                                .sharesHeld(0)
                                .avgCostBasis(BigDecimal.ZERO)
                                .build();
                    });

            coreHoldingRepository.save(holding);
        }

        log.info("Core holdings initialization complete. Total holdings: {}",
                coreHoldingRepository.count());
    }

    /**
     * Returns core holdings filtered by market (NGX or US).
     *
     * @param market the market identifier ("NGX" or "US")
     * @return list of core holdings in the specified market
     */
    public List<CoreHolding> getHoldingsByMarket(String market) {
        List<CoreHolding> holdings = coreHoldingRepository.findByMarket(market);
        log.debug("Found {} core holdings for market {}", holdings.size(), market);
        return holdings;
    }

    /**
     * Updates currentWeightPct and marketValue for all core holdings based on current
     * prices from open {@link Position} entities.
     * <p>
     * Market value = sharesHeld * currentPrice (from Position).
     * Current weight = (holding market value / total core value) * 100.
     */
    @Transactional
    public void updateMarketValues() {
        List<CoreHolding> allHoldings = coreHoldingRepository.findAll();

        if (allHoldings.isEmpty()) {
            log.warn("No core holdings found. Cannot update market values.");
            return;
        }

        // First pass: update market values from position data
        for (CoreHolding holding : allHoldings) {
            List<Position> positions = positionRepository.findBySymbolAndIsOpenTrue(holding.getSymbol());

            if (positions.isEmpty()) {
                holding.setMarketValue(BigDecimal.ZERO);
                holding.setSharesHeld(0);
            } else {
                int totalShares = positions.stream()
                        .mapToInt(Position::getQuantity)
                        .sum();
                BigDecimal currentPrice = positions.get(0).getCurrentPrice();

                if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal marketValue = currentPrice.multiply(BigDecimal.valueOf(totalShares));
                    holding.setMarketValue(marketValue.setScale(2, RoundingMode.HALF_UP));
                    holding.setSharesHeld(totalShares);
                } else {
                    // Fallback: use avgCostBasis if current price is not available
                    BigDecimal fallbackValue = holding.getAvgCostBasis()
                            .multiply(BigDecimal.valueOf(totalShares));
                    holding.setMarketValue(fallbackValue.setScale(2, RoundingMode.HALF_UP));
                    holding.setSharesHeld(totalShares);
                    log.warn("No current price available for {}. Using avg cost basis for market value.", holding.getSymbol());
                }
            }
        }

        // Second pass: calculate weights based on total value
        BigDecimal totalValue = allHoldings.stream()
                .map(CoreHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Total core portfolio value is zero. Setting all weights to 0.");
            allHoldings.forEach(h -> h.setCurrentWeightPct(BigDecimal.ZERO));
        } else {
            for (CoreHolding holding : allHoldings) {
                BigDecimal weight = holding.getMarketValue()
                        .divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                holding.setCurrentWeightPct(weight);
            }
        }

        // Persist all updates
        for (CoreHolding holding : allHoldings) {
            holding.setUpdatedAt(LocalDateTime.now());
        }
        coreHoldingRepository.saveAll(allHoldings);

        log.info("Updated market values for {} core holdings. Total core value: {}",
                allHoldings.size(), totalValue.toPlainString());
    }

    /**
     * Returns the sum of all core holding market values.
     *
     * @return total market value of the core portfolio
     */
    public BigDecimal getTotalCoreValue() {
        BigDecimal total = coreHoldingRepository.findAll().stream()
                .map(CoreHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("Total core portfolio value: {}", total.toPlainString());
        return total;
    }

    /**
     * Returns a list of core holdings where the absolute drift between currentWeightPct
     * and targetWeightPct exceeds the configured drift threshold.
     *
     * @return holdings that have drifted beyond the threshold
     */
    public List<CoreHolding> getDriftReport() {
        BigDecimal driftThreshold = longtermProperties.getRebalance().getDriftThresholdPct();
        List<CoreHolding> allHoldings = coreHoldingRepository.findAllByOrderByMarketAscSymbolAsc();
        List<CoreHolding> driftedHoldings = new ArrayList<>();

        for (CoreHolding holding : allHoldings) {
            BigDecimal current = holding.getCurrentWeightPct() != null
                    ? holding.getCurrentWeightPct() : BigDecimal.ZERO;
            BigDecimal target = holding.getTargetWeightPct() != null
                    ? holding.getTargetWeightPct() : BigDecimal.ZERO;
            BigDecimal drift = current.subtract(target).abs();

            if (drift.compareTo(driftThreshold) > 0) {
                driftedHoldings.add(holding);
                log.info("Drift detected for {}:{} — current: {}%, target: {}%, drift: {}%",
                        holding.getSymbol(), holding.getMarket(),
                        current, target, drift);
            }
        }

        log.info("Drift report: {} of {} holdings exceed {}% threshold",
                driftedHoldings.size(), allHoldings.size(), driftThreshold);
        return driftedHoldings;
    }
}
