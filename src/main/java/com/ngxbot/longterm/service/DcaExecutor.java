package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.DcaPlan;
import com.ngxbot.longterm.repository.DcaPlanRepository;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Handles multi-currency Dollar Cost Averaging (DCA) execution for both NGX and US markets.
 * <p>
 * Allocates the configured monthly budget proportionally across active DCA plans for each
 * market, checks available settlement cash, and generates trade allocation results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DcaExecutor {

    private final DcaPlanRepository dcaPlanRepository;
    private final PositionRepository positionRepository;
    private final SettlementCashTracker settlementCashTracker;
    private final LongtermProperties longtermProperties;

    /**
     * Represents the result of a single DCA allocation for a symbol.
     */
    public record DcaAllocation(String symbol, String market, String currency,
                                int quantity, BigDecimal pricePerShare,
                                BigDecimal totalAmount) {
    }

    /**
     * Executes NGX DCA for the given date. Allocates the configured NGN monthly budget
     * across active NGX DCA plans.
     *
     * @param date the execution date
     * @return list of DCA allocations generated
     */
    @Transactional
    public List<DcaAllocation> executeNgxDca(LocalDate date) {
        if (!longtermProperties.getDca().isEnabled()) {
            log.info("DCA is disabled. Skipping NGX DCA execution for {}", date);
            return List.of();
        }

        BigDecimal totalBudget = longtermProperties.getDca().getNgxBudgetNairaMonthly();
        log.info("Executing NGX DCA for date {} with total budget {} NGN", date, totalBudget.toPlainString());

        return allocateBudget("NGX", totalBudget, date);
    }

    /**
     * Executes US DCA for the given date. Allocates the configured USD monthly budget
     * across active US DCA plans.
     *
     * @param date the execution date
     * @return list of DCA allocations generated
     */
    @Transactional
    public List<DcaAllocation> executeUsDca(LocalDate date) {
        if (!longtermProperties.getDca().isEnabled()) {
            log.info("DCA is disabled. Skipping US DCA execution for {}", date);
            return List.of();
        }

        BigDecimal totalBudget = longtermProperties.getDca().getUsBudgetUsdMonthly();
        log.info("Executing US DCA for date {} with total budget {} USD", date, totalBudget.toPlainString());

        return allocateBudget("US", totalBudget, date);
    }

    /**
     * Distributes the total budget proportionally by weightPct to each active DCA plan
     * for the specified market. Checks {@link SettlementCashTracker} for available cash
     * and caps the budget accordingly. Returns a list of DCA allocation results (symbol,
     * quantity, price).
     *
     * @param market      the market identifier ("NGX" or "US")
     * @param totalBudget the total budget to allocate
     * @param date        the execution date
     * @return list of DCA allocations
     */
    @Transactional
    public List<DcaAllocation> allocateBudget(String market, BigDecimal totalBudget, LocalDate date) {
        List<DcaPlan> activePlans = dcaPlanRepository.findByMarketAndIsActiveTrue(market);

        if (activePlans.isEmpty()) {
            log.warn("No active DCA plans found for market {}. Skipping allocation.", market);
            return List.of();
        }

        // Check available cash via SettlementCashTracker
        StrategyMarket strategyMarket = "NGX".equals(market) ? StrategyMarket.NGX : StrategyMarket.US;
        BigDecimal availableCash = settlementCashTracker.getAvailableCash(strategyMarket);
        String currency = "NGX".equals(market) ? "NGN" : "USD";

        if (availableCash.compareTo(totalBudget) < 0) {
            log.warn("Insufficient available cash for {} DCA. Available: {} {}, Required: {} {}. "
                            + "Reducing budget to available amount.",
                    market, availableCash.toPlainString(), currency,
                    totalBudget.toPlainString(), currency);
            totalBudget = availableCash;
        }

        if (totalBudget.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No cash available for {} DCA execution. Skipping.", market);
            return List.of();
        }

        // Calculate total weight for normalization
        BigDecimal totalWeight = activePlans.stream()
                .map(DcaPlan::getWeightPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Total weight for {} DCA plans is zero. Cannot allocate.", market);
            return List.of();
        }

        List<DcaAllocation> allocations = new ArrayList<>();

        for (DcaPlan plan : activePlans) {
            // Proportional budget = totalBudget * (plan.weightPct / totalWeight)
            BigDecimal planBudget = totalBudget
                    .multiply(plan.getWeightPct())
                    .divide(totalWeight, 2, RoundingMode.HALF_DOWN);

            // Get current price from open positions
            BigDecimal currentPrice = getCurrentPrice(plan.getSymbol());

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("No valid current price for {}. Skipping DCA allocation.", plan.getSymbol());
                continue;
            }

            int shares = calculateShares(planBudget, currentPrice);

            if (shares <= 0) {
                log.info("Budget {} {} insufficient for even 1 share of {} at {} {}. Skipping.",
                        planBudget.toPlainString(), currency, plan.getSymbol(),
                        currentPrice.toPlainString(), currency);
                continue;
            }

            BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(shares))
                    .setScale(2, RoundingMode.HALF_UP);

            DcaAllocation allocation = new DcaAllocation(
                    plan.getSymbol(), market, currency, shares, currentPrice, totalAmount);
            allocations.add(allocation);

            // Update DCA plan tracking
            plan.setLastExecutionDate(date);
            plan.setTotalInvested(plan.getTotalInvested().add(totalAmount));
            dcaPlanRepository.save(plan);

            log.info("DCA allocation -- market: {}, currency: {}, symbol: {}, quantity: {}, "
                            + "price: {}, amount: {}",
                    market, currency, plan.getSymbol(), shares,
                    currentPrice.toPlainString(), totalAmount.toPlainString());
        }

        BigDecimal totalAllocated = allocations.stream()
                .map(DcaAllocation::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("DCA execution complete for market {}. {} allocations totalling {} {}",
                market, allocations.size(), totalAllocated.toPlainString(), currency);

        return allocations;
    }

    /**
     * Calculates the number of whole shares purchasable with the given budget at the given price.
     * Returns floor(budget / price).
     *
     * @param budget       the available budget
     * @param currentPrice the price per share
     * @return number of whole shares (floor division)
     */
    private int calculateShares(BigDecimal budget, BigDecimal currentPrice) {
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return budget.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();
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
