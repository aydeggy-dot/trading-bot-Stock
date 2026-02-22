package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.entity.RebalanceAction;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import com.ngxbot.longterm.repository.RebalanceActionRepository;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.strategy.StrategyMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-market portfolio rebalancer.
 * <p>
 * Compares current portfolio weights against target allocations, identifies drift,
 * and generates rebalance actions (BUY for underweight, SELL for overweight).
 * Supports the "use new cash first" strategy to minimize selling.
 * <p>
 * If {@link LongtermProperties.Rebalance#isRequireApproval()} is true, actions are
 * created with PENDING status and must be approved before execution. Otherwise,
 * they are created as APPROVED and can be executed immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioRebalancer {

    private static final String ACTION_BUY = "BUY";
    private static final String ACTION_SELL = "SELL";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_EXECUTED = "EXECUTED";

    private final CoreHoldingRepository coreHoldingRepository;
    private final RebalanceActionRepository rebalanceActionRepository;
    private final PositionRepository positionRepository;
    private final SettlementCashTracker settlementCashTracker;
    private final LongtermProperties longtermProperties;

    /**
     * Checks all core holdings for drift and generates rebalance actions if any holding
     * exceeds the configured drift threshold.
     * <p>
     * For each holding:
     * <ul>
     *   <li>Positive drift (overweight) generates a SELL action</li>
     *   <li>Negative drift (underweight) generates a BUY action</li>
     * </ul>
     * Actions are persisted with PENDING status if approval is required, otherwise APPROVED.
     *
     * @param date the date triggering the rebalance check
     * @return list of generated rebalance actions
     */
    @Transactional
    public List<RebalanceAction> checkAndRebalance(LocalDate date) {
        BigDecimal driftThreshold = longtermProperties.getRebalance().getDriftThresholdPct();
        List<CoreHolding> allHoldings = coreHoldingRepository.findAll();

        if (allHoldings.isEmpty()) {
            log.info("No core holdings found. Skipping rebalance check.");
            return List.of();
        }

        log.info("Running rebalance check for {} with drift threshold {}% across {} holdings",
                date, driftThreshold, allHoldings.size());

        List<RebalanceAction> actions = new ArrayList<>();

        for (CoreHolding holding : allHoldings) {
            BigDecimal drift = calculateDrift(holding);
            BigDecimal absDrift = drift.abs();

            if (absDrift.compareTo(driftThreshold) > 0) {
                String actionType;
                if (drift.compareTo(BigDecimal.ZERO) > 0) {
                    actionType = ACTION_SELL; // overweight -> sell
                } else {
                    actionType = ACTION_BUY;  // underweight -> buy
                }

                // Calculate quantity needed to bring back to target
                int quantity = calculateRebalanceQuantity(holding, drift);

                if (quantity <= 0) {
                    log.debug("Calculated quantity is 0 for {}:{}. Skipping.", holding.getSymbol(), holding.getMarket());
                    continue;
                }

                BigDecimal estimatedValue = getEstimatedValue(holding, quantity);

                String initialStatus = longtermProperties.getRebalance().isRequireApproval()
                        ? STATUS_PENDING : STATUS_APPROVED;

                RebalanceAction action = RebalanceAction.builder()
                        .triggerDate(date)
                        .symbol(holding.getSymbol())
                        .market(holding.getMarket())
                        .actionType(actionType)
                        .currentWeightPct(holding.getCurrentWeightPct())
                        .targetWeightPct(holding.getTargetWeightPct())
                        .driftPct(drift)
                        .quantity(quantity)
                        .estimatedValue(estimatedValue)
                        .status(initialStatus)
                        .build();

                actions.add(rebalanceActionRepository.save(action));

                log.info("Rebalance action created: {} {} shares of {}:{} -- "
                                + "current: {}%, target: {}%, drift: {}%, estimated value: {}, status: {}",
                        actionType, quantity, holding.getSymbol(), holding.getMarket(),
                        holding.getCurrentWeightPct(), holding.getTargetWeightPct(),
                        drift, estimatedValue.toPlainString(), initialStatus);
            }
        }

        log.info("Rebalance check complete. Generated {} actions for {}", actions.size(), date);
        return actions;
    }

    /**
     * Calculates the drift for a single core holding.
     * <p>
     * Drift = currentWeightPct - targetWeightPct.
     * Positive drift means overweight, negative means underweight.
     *
     * @param holding the core holding
     * @return the drift percentage (positive = overweight, negative = underweight)
     */
    public BigDecimal calculateDrift(CoreHolding holding) {
        BigDecimal current = holding.getCurrentWeightPct() != null
                ? holding.getCurrentWeightPct() : BigDecimal.ZERO;
        BigDecimal target = holding.getTargetWeightPct() != null
                ? holding.getTargetWeightPct() : BigDecimal.ZERO;

        return current.subtract(target).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generates all rebalance actions needed to bring the portfolio back to target allocations.
     * <p>
     * If {@link LongtermProperties.Rebalance#isUseNewCashFirst()} is true, BUY actions
     * are prioritized and available cash is checked first.
     *
     * @param date the trigger date
     * @return list of required rebalance actions
     */
    @Transactional
    public List<RebalanceAction> generateRebalanceActions(LocalDate date) {
        List<CoreHolding> allHoldings = coreHoldingRepository.findAll();
        BigDecimal driftThreshold = longtermProperties.getRebalance().getDriftThresholdPct();
        boolean useNewCashFirst = longtermProperties.getRebalance().isUseNewCashFirst();

        List<RebalanceAction> buyActions = new ArrayList<>();
        List<RebalanceAction> sellActions = new ArrayList<>();

        for (CoreHolding holding : allHoldings) {
            BigDecimal drift = calculateDrift(holding);
            BigDecimal absDrift = drift.abs();

            if (absDrift.compareTo(driftThreshold) <= 0) {
                continue;
            }

            int quantity = calculateRebalanceQuantity(holding, drift);
            if (quantity <= 0) {
                continue;
            }

            BigDecimal estimatedValue = getEstimatedValue(holding, quantity);

            String actionType = drift.compareTo(BigDecimal.ZERO) > 0 ? ACTION_SELL : ACTION_BUY;
            String initialStatus = longtermProperties.getRebalance().isRequireApproval()
                    ? STATUS_PENDING : STATUS_APPROVED;

            RebalanceAction action = RebalanceAction.builder()
                    .triggerDate(date)
                    .symbol(holding.getSymbol())
                    .market(holding.getMarket())
                    .actionType(actionType)
                    .currentWeightPct(holding.getCurrentWeightPct())
                    .targetWeightPct(holding.getTargetWeightPct())
                    .driftPct(drift)
                    .quantity(quantity)
                    .estimatedValue(estimatedValue)
                    .status(initialStatus)
                    .build();

            if (ACTION_BUY.equals(actionType)) {
                buyActions.add(action);
            } else {
                sellActions.add(action);
            }
        }

        // If useNewCashFirst, check available cash for BUY actions before requiring SELLs
        if (useNewCashFirst && !buyActions.isEmpty()) {
            BigDecimal ngxCash = settlementCashTracker.getAvailableCash(StrategyMarket.NGX);
            BigDecimal usCash = settlementCashTracker.getAvailableCash(StrategyMarket.US);

            BigDecimal ngxBuyTotal = buyActions.stream()
                    .filter(a -> "NGX".equals(a.getMarket()))
                    .map(RebalanceAction::getEstimatedValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal usBuyTotal = buyActions.stream()
                    .filter(a -> "US".equals(a.getMarket()))
                    .map(RebalanceAction::getEstimatedValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("UseNewCashFirst enabled. NGX cash: {}, NGX buy total: {}. "
                            + "US cash: {}, US buy total: {}",
                    ngxCash.toPlainString(), ngxBuyTotal.toPlainString(),
                    usCash.toPlainString(), usBuyTotal.toPlainString());

            // If we have enough cash for all buys, we may not need the sell actions
            if (ngxCash.compareTo(ngxBuyTotal) >= 0 && usCash.compareTo(usBuyTotal) >= 0) {
                log.info("Sufficient cash available for all BUY actions. SELL actions may be deferred.");
            }
        }

        // Persist all actions
        List<RebalanceAction> allActions = new ArrayList<>();
        allActions.addAll(buyActions);
        allActions.addAll(sellActions);

        List<RebalanceAction> saved = rebalanceActionRepository.saveAll(allActions);
        log.info("Generated {} rebalance actions ({} BUY, {} SELL) for {}",
                saved.size(), buyActions.size(), sellActions.size(), date);

        return saved;
    }

    /**
     * Finds RebalanceActions with status=APPROVED and executes them.
     * <p>
     * On execution, each action is marked with status=EXECUTED and a timestamp.
     * The corresponding core holding's lastRebalanceDate is also updated.
     */
    @Transactional
    public void executeApprovedActions() {
        List<RebalanceAction> approvedActions = rebalanceActionRepository.findByStatus(STATUS_APPROVED);

        if (approvedActions.isEmpty()) {
            log.info("No approved rebalance actions to execute.");
            return;
        }

        log.info("Executing {} approved rebalance actions", approvedActions.size());

        for (RebalanceAction action : approvedActions) {
            try {
                log.info("Executing rebalance: {} {} shares of {}:{} (drift: {}%)",
                        action.getActionType(), action.getQuantity(),
                        action.getSymbol(), action.getMarket(), action.getDriftPct());

                // Mark as executed
                action.setStatus(STATUS_EXECUTED);
                action.setExecutedAt(LocalDateTime.now());
                rebalanceActionRepository.save(action);

                // Update the core holding's last rebalance date
                coreHoldingRepository.findBySymbolAndMarket(action.getSymbol(), action.getMarket())
                        .ifPresent(holding -> {
                            holding.setLastRebalanceDate(LocalDate.now());
                            holding.setUpdatedAt(LocalDateTime.now());
                            coreHoldingRepository.save(holding);
                        });

                log.info("Successfully executed rebalance action {} for {}:{}",
                        action.getId(), action.getSymbol(), action.getMarket());

            } catch (Exception e) {
                log.error("Failed to execute rebalance action {} for {}:{}: {}",
                        action.getId(), action.getSymbol(), action.getMarket(), e.getMessage(), e);
            }
        }
    }

    /**
     * Calculates the number of shares needed to correct the drift for a holding.
     *
     * @param holding the core holding
     * @param drift   the drift percentage (positive = overweight, negative = underweight)
     * @return the number of shares to buy or sell
     */
    private int calculateRebalanceQuantity(CoreHolding holding, BigDecimal drift) {
        // Get current price
        BigDecimal currentPrice = getCurrentPrice(holding.getSymbol());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No current price for {}. Cannot calculate rebalance quantity.", holding.getSymbol());
            return 0;
        }

        // Total portfolio value approximation
        BigDecimal totalValue = coreHoldingRepository.findAll().stream()
                .map(CoreHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        // Value to adjust = totalValue * |drift| / 100
        BigDecimal valueToAdjust = totalValue
                .multiply(drift.abs())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Shares = valueToAdjust / currentPrice
        return valueToAdjust.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();
    }

    /**
     * Estimates the value of a rebalance action.
     *
     * @param holding  the core holding
     * @param quantity the number of shares
     * @return the estimated monetary value
     */
    private BigDecimal getEstimatedValue(CoreHolding holding, int quantity) {
        BigDecimal currentPrice = getCurrentPrice(holding.getSymbol());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Retrieves the current price for a symbol from open positions.
     *
     * @param symbol the stock symbol
     * @return current price, or null if no open position exists
     */
    private BigDecimal getCurrentPrice(String symbol) {
        List<Position> positions = positionRepository.findBySymbolAndIsOpenTrue(symbol);
        if (positions.isEmpty()) {
            return null;
        }
        return positions.get(0).getCurrentPrice();
    }
}
