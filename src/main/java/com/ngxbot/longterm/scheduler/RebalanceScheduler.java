package com.ngxbot.longterm.scheduler;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.RebalanceAction;
import com.ngxbot.longterm.service.CorePortfolioManager;
import com.ngxbot.longterm.service.PortfolioRebalancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Quarterly rebalance scheduler for the core long-term portfolio.
 * <p>
 * Runs on the 1st day of each quarter (January, April, July, October)
 * at 3:00 PM WAT. First refreshes all market values, then checks portfolio
 * drift and generates rebalance actions as needed.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RebalanceScheduler {

    private final PortfolioRebalancer portfolioRebalancer;
    private final CorePortfolioManager corePortfolioManager;
    private final LongtermProperties longtermProperties;

    /**
     * Quarterly rebalance check — runs at 3:00 PM WAT on the 1st of Jan, Apr, Jul, Oct.
     */
    @Scheduled(cron = "0 0 15 1 1,4,7,10 *", zone = "Africa/Lagos")
    public void quarterlyRebalanceCheck() {
        LocalDate today = LocalDate.now();
        log.info("=== QUARTERLY REBALANCE CHECK STARTED for {} ===", today);
        log.info("Rebalance config — frequency: {}, drift threshold: {}%, method: {}, require approval: {}",
                longtermProperties.getRebalance().getFrequency(),
                longtermProperties.getRebalance().getDriftThresholdPct(),
                longtermProperties.getRebalance().getMethod(),
                longtermProperties.getRebalance().isRequireApproval());

        try {
            // Step 1: Refresh market values for all core holdings
            log.info("Updating market values for all core holdings...");
            corePortfolioManager.updateMarketValues();

            // Step 2: Check drift and generate rebalance actions
            log.info("Checking portfolio drift and generating rebalance actions...");
            List<RebalanceAction> actions = portfolioRebalancer.checkAndRebalance(today);

            if (actions.isEmpty()) {
                log.info("No rebalance actions needed. Portfolio is within drift threshold.");
            } else {
                log.info("Generated {} rebalance actions:", actions.size());
                for (RebalanceAction action : actions) {
                    log.info("  {} {} — action: {}, current: {}%, target: {}%, drift: {}%, qty: {}, est. value: {}",
                            action.getMarket(), action.getSymbol(),
                            action.getActionType(),
                            action.getCurrentWeightPct(),
                            action.getTargetWeightPct(),
                            action.getDriftPct(),
                            action.getQuantity(),
                            action.getEstimatedValue());
                }
            }

            log.info("=== QUARTERLY REBALANCE CHECK COMPLETE for {} — {} actions generated ===",
                    today, actions.size());
        } catch (Exception e) {
            log.error("Quarterly rebalance check failed for {}: {}", today, e.getMessage(), e);
        }
    }
}
