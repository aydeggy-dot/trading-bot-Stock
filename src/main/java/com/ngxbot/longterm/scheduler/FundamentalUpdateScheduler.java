package com.ngxbot.longterm.scheduler;

import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.service.CorePortfolioManager;
import com.ngxbot.longterm.service.FundamentalScreener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekly scheduler that refreshes fundamental scores for all core holdings.
 * <p>
 * Runs every Sunday at 8:00 PM WAT. Iterates through all core holdings
 * and calls the fundamental screener for each symbol. The resulting scores
 * are logged for monitoring purposes.
 * </p>
 * <p>
 * This is a placeholder scheduler for future integration with fundamental
 * data providers (e.g., EODHD fundamentals, NGX annual reports).
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundamentalUpdateScheduler {

    private final FundamentalScreener fundamentalScreener;
    private final CorePortfolioManager corePortfolioManager;

    /**
     * Weekly fundamental update — runs every Sunday at 8:00 PM WAT.
     */
    @Scheduled(cron = "0 0 20 * * SUN", zone = "Africa/Lagos")
    public void weeklyFundamentalUpdate() {
        log.info("=== WEEKLY FUNDAMENTAL UPDATE STARTED ===");

        try {
            List<CoreHolding> allHoldings = new ArrayList<>(corePortfolioManager.getHoldingsByMarket("NGX"));
            allHoldings.addAll(corePortfolioManager.getHoldingsByMarket("US"));

            if (allHoldings.isEmpty()) {
                log.info("No core holdings found. Skipping fundamental update.");
                return;
            }

            log.info("Screening fundamentals for {} core holdings...", allHoldings.size());

            int screened = 0;
            int failed = 0;

            for (CoreHolding holding : allHoldings) {
                try {
                    BigDecimal score = fundamentalScreener.screenSymbol(holding.getSymbol());
                    log.info("  {}:{} — fundamental score: {}/100 (target weight: {}%, current weight: {}%)",
                            holding.getMarket(), holding.getSymbol(),
                            score,
                            holding.getTargetWeightPct(),
                            holding.getCurrentWeightPct());
                    screened++;
                } catch (Exception e) {
                    log.warn("  Failed to screen {}: {}", holding.getSymbol(), e.getMessage());
                    failed++;
                }
            }

            log.info("=== WEEKLY FUNDAMENTAL UPDATE COMPLETE — screened: {}, failed: {} ===",
                    screened, failed);
        } catch (Exception e) {
            log.error("Weekly fundamental update failed: {}", e.getMessage(), e);
        }
    }
}
