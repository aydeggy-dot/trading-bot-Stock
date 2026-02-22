package com.ngxbot.longterm.scheduler;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.DividendEvent;
import com.ngxbot.longterm.service.DividendReinvestmentService;
import com.ngxbot.longterm.service.DividendTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Weekly scheduler for dividend tracking and reinvestment processing.
 * <p>
 * Runs every Monday at 9:00 AM WAT. Performs two tasks:
 * <ol>
 *     <li>Fetches upcoming ex-dividend dates and logs alerts for holdings
 *         with ex-dates approaching within the configured alert window.</li>
 *     <li>Processes any pending dividend reinvestments where dividends have
 *         been received but not yet reinvested.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendCheckScheduler {

    private final DividendTracker dividendTracker;
    private final DividendReinvestmentService dividendReinvestmentService;
    private final LongtermProperties longtermProperties;

    /**
     * Weekly dividend check — runs every Monday at 9:00 AM WAT.
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Africa/Lagos")
    public void weeklyDividendCheck() {
        LongtermProperties.Dividend dividendConfig = longtermProperties.getDividend();
        int alertDays = dividendConfig.getAlertDaysBeforeExDate();

        log.info("=== WEEKLY DIVIDEND CHECK STARTED — alert window: {} days ===", alertDays);

        try {
            // Step 1: Check for upcoming ex-dividend dates
            if (dividendConfig.isTrackExDates()) {
                log.info("Checking for upcoming ex-dividend dates within {} days...", alertDays);
                List<DividendEvent> upcomingExDates = dividendTracker.getUpcomingExDates(alertDays);

                if (upcomingExDates.isEmpty()) {
                    log.info("No upcoming ex-dividend dates within the next {} days.", alertDays);
                } else {
                    log.info("Found {} upcoming ex-dividend events:", upcomingExDates.size());
                    for (DividendEvent event : upcomingExDates) {
                        log.info("  ALERT: {} ({}) — ex-date: {}, payment date: {}, dividend/share: {} {}",
                                event.getSymbol(), event.getMarket(),
                                event.getExDate(), event.getPaymentDate(),
                                event.getDividendPerShare(), event.getCurrency());
                    }
                }
            } else {
                log.debug("Ex-date tracking is disabled. Skipping ex-date check.");
            }

            // Step 2: Process pending dividend reinvestments
            if (dividendConfig.isReinvest()) {
                log.info("Processing pending dividend reinvestments (reinvest into: {})...",
                        dividendConfig.getReinvestInto());
                dividendReinvestmentService.processReinvestments();
                log.info("Dividend reinvestment processing complete.");
            } else {
                log.debug("Dividend reinvestment is disabled. Skipping reinvestment processing.");
            }

            log.info("=== WEEKLY DIVIDEND CHECK COMPLETE ===");
        } catch (Exception e) {
            log.error("Weekly dividend check failed: {}", e.getMessage(), e);
        }
    }
}
