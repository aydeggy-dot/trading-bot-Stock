package com.ngxbot.discovery.scheduler;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.discovery.client.EodhdScreenerClient;
import com.ngxbot.discovery.client.EodhdScreenerClient.ScreenerResult;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.service.CandidateEvaluator;
import com.ngxbot.discovery.service.DemotionPolicy;
import com.ngxbot.discovery.service.PromotionPolicy;
import com.ngxbot.discovery.service.WatchlistManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryScheduler {

    private final EodhdScreenerClient eodhdScreenerClient;
    private final CandidateEvaluator candidateEvaluator;
    private final PromotionPolicy promotionPolicy;
    private final DemotionPolicy demotionPolicy;
    private final DiscoveredStockRepository discoveredStockRepository;
    private final DiscoveryProperties discoveryProperties;
    private final WatchlistManager watchlistManager;

    /**
     * Weekly screener scan - runs every Sunday at 06:00 WAT.
     * Fetches stock data from EODHD screener and evaluates candidates.
     */
    @Scheduled(cron = "0 0 6 * * SUN", zone = "Africa/Lagos")
    public void weeklyScreenerScan() {
        if (!discoveryProperties.isEnabled()) {
            log.debug("Discovery module is disabled, skipping weekly screener scan");
            return;
        }

        log.info("Starting weekly screener scan...");

        try {
            List<ScreenerResult> results = eodhdScreenerClient.screenNgxStocks();
            log.info("Screener returned {} raw results", results.size());

            if (results.isEmpty()) {
                log.warn("Screener returned no results, check API connectivity");
                return;
            }

            List<DiscoveredStock> qualified = candidateEvaluator.evaluateScreenerResults(results);

            // Also check expired cooldowns and clear them
            List<DiscoveredStock> expiredCooldowns = discoveredStockRepository
                    .findByCooldownUntilBeforeAndStatus(LocalDate.now(), WatchlistStatus.DEMOTED.name());
            for (DiscoveredStock stock : expiredCooldowns) {
                stock.setStatus(WatchlistStatus.CANDIDATE.name());
                stock.setCooldownUntil(null);
                discoveredStockRepository.save(stock);
                log.info("Cooldown expired for {}, reset to CANDIDATE", stock.getSymbol());
            }

            // Log summary stats
            long candidateCount = discoveredStockRepository.countByStatus(WatchlistStatus.CANDIDATE.name());
            long observationCount = discoveredStockRepository.countByStatus(WatchlistStatus.OBSERVATION.name());
            long promotedCount = discoveredStockRepository.countByStatus(WatchlistStatus.PROMOTED.name());
            long demotedCount = discoveredStockRepository.countByStatus(WatchlistStatus.DEMOTED.name());

            log.info("Weekly screener scan complete. New qualified: {}. " +
                            "Pipeline stats - Candidates: {}, Observation: {}, Promoted: {}, Demoted: {}",
                    qualified.size(), candidateCount, observationCount, promotedCount, demotedCount);

        } catch (Exception e) {
            log.error("Error during weekly screener scan: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily observation check - runs Monday-Friday at 20:00 WAT.
     * Reviews all stocks in OBSERVATION status to determine if they qualify for promotion.
     */
    @Scheduled(cron = "0 0 20 * * MON-FRI", zone = "Africa/Lagos")
    public void dailyObservationCheck() {
        if (!discoveryProperties.isEnabled()) {
            log.debug("Discovery module is disabled, skipping daily observation check");
            return;
        }

        log.info("Starting daily observation check...");

        try {
            List<DiscoveredStock> observationStocks = discoveredStockRepository
                    .findByStatus(WatchlistStatus.OBSERVATION.name());

            int promotedCount = 0;

            for (DiscoveredStock stock : observationStocks) {
                if (promotionPolicy.checkForPromotion(stock)) {
                    promotionPolicy.promote(stock);
                    promotedCount++;
                }
            }

            log.info("Daily observation check complete. Checked: {}, Promoted: {}",
                    observationStocks.size(), promotedCount);

        } catch (Exception e) {
            log.error("Error during daily observation check: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily demotion check - runs Monday-Friday at 20:30 WAT.
     * Reviews all PROMOTED stocks to determine if they should be demoted.
     */
    @Scheduled(cron = "0 30 20 * * MON-FRI", zone = "Africa/Lagos")
    public void dailyDemotionCheck() {
        if (!discoveryProperties.isEnabled()) {
            log.debug("Discovery module is disabled, skipping daily demotion check");
            return;
        }

        log.info("Starting daily demotion check...");

        try {
            List<DiscoveredStock> promotedStocks = discoveredStockRepository
                    .findByStatus(WatchlistStatus.PROMOTED.name());

            int demotedCount = 0;

            for (DiscoveredStock stock : promotedStocks) {
                if (demotionPolicy.checkForDemotion(stock)) {
                    String reason = buildDemotionReason(stock);
                    demotionPolicy.demote(stock, reason);
                    demotedCount++;
                }
            }

            log.info("Daily demotion check complete. Checked: {}, Demoted: {}",
                    promotedStocks.size(), demotedCount);

        } catch (Exception e) {
            log.error("Error during daily demotion check: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds a human-readable demotion reason based on the stock's state.
     */
    private String buildDemotionReason(DiscoveredStock stock) {
        StringBuilder reason = new StringBuilder();

        if (stock.getLastSignalDate() != null) {
            long daysSinceSignal = java.time.temporal.ChronoUnit.DAYS
                    .between(stock.getLastSignalDate(), LocalDate.now());
            if (daysSinceSignal >= discoveryProperties.getNoSignalDemotionDays()) {
                reason.append("No signals for ").append(daysSinceSignal).append(" days. ");
            }
        } else {
            reason.append("Never received a signal. ");
        }

        if (stock.getFundamentalScore() != null
                && stock.getFundamentalScore().doubleValue() < discoveryProperties.getMinFundamentalScore()) {
            reason.append("Fundamental score ")
                    .append(stock.getFundamentalScore())
                    .append(" below threshold ")
                    .append(discoveryProperties.getMinFundamentalScore())
                    .append(". ");
        }

        return reason.length() > 0 ? reason.toString().trim() : "Automated demotion check";
    }
}
