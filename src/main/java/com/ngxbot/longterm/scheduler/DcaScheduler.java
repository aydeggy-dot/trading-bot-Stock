package com.ngxbot.longterm.scheduler;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.service.DcaExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Scheduler for Dollar-Cost Averaging (DCA) execution.
 * <p>
 * Runs daily at 10:15 AM WAT but only triggers DCA execution when
 * today matches the configured execution day for the NGX or US market.
 * If the execution day falls on a weekend, the fallback strategy determines
 * whether to execute on the next Monday.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DcaScheduler {

    private final DcaExecutor dcaExecutor;
    private final LongtermProperties longtermProperties;

    /**
     * Runs daily at 10:15 AM WAT. Checks if today is a DCA execution day
     * for either NGX or US market and triggers the corresponding DCA run.
     */
    @Scheduled(cron = "0 15 10 * * *", zone = "Africa/Lagos")
    public void checkAndExecuteDca() {
        LongtermProperties.Dca dcaConfig = longtermProperties.getDca();

        if (!dcaConfig.isEnabled()) {
            log.debug("DCA is disabled. Skipping execution check.");
            return;
        }

        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();

        log.info("DCA scheduler triggered for {}. NGX execution day: {}, US execution day: {}",
                today, dcaConfig.getNgxExecutionDay(), dcaConfig.getUsExecutionDay());

        // Check NGX DCA execution
        if (isExecutionDay(today, dcaConfig.getNgxExecutionDay(), dcaConfig.getFallbackDayIfWeekend())) {
            log.info("Today is NGX DCA execution day. Triggering NGX DCA for {}", today);
            try {
                dcaExecutor.executeNgxDca(today);
                log.info("NGX DCA execution completed successfully for {}", today);
            } catch (Exception e) {
                log.error("NGX DCA execution failed for {}: {}", today, e.getMessage(), e);
            }
        }

        // Check US DCA execution
        if (isExecutionDay(today, dcaConfig.getUsExecutionDay(), dcaConfig.getFallbackDayIfWeekend())) {
            log.info("Today is US DCA execution day. Triggering US DCA for {}", today);
            try {
                dcaExecutor.executeUsDca(today);
                log.info("US DCA execution completed successfully for {}", today);
            } catch (Exception e) {
                log.error("US DCA execution failed for {}: {}", today, e.getMessage(), e);
            }
        }
    }

    /**
     * Determines if today is the effective execution day, accounting for weekends.
     * <p>
     * If the configured execution day falls on a Saturday or Sunday and the
     * fallback strategy is "NEXT", the effective execution day becomes the following Monday.
     * </p>
     *
     * @param today              the current date
     * @param configuredDay      the configured day-of-month for DCA execution
     * @param fallbackStrategy   the weekend fallback strategy ("NEXT" or "PREVIOUS")
     * @return true if today is the effective execution day
     */
    private boolean isExecutionDay(LocalDate today, int configuredDay, String fallbackStrategy) {
        int dayOfMonth = today.getDayOfMonth();

        // Direct match — configured day is today and today is a weekday
        if (dayOfMonth == configuredDay && isWeekday(today)) {
            return true;
        }

        // Check if the configured day fell on a weekend this month
        int lastDayOfMonth = today.lengthOfMonth();
        int effectiveDay = Math.min(configuredDay, lastDayOfMonth);
        LocalDate configuredDate = today.withDayOfMonth(effectiveDay);

        if (isWeekday(configuredDate)) {
            // The configured day is a weekday; only execute on that exact day
            return dayOfMonth == effectiveDay;
        }

        // Configured day is a weekend — apply fallback
        LocalDate fallbackDate;
        if ("PREVIOUS".equalsIgnoreCase(fallbackStrategy)) {
            fallbackDate = getPreviousWeekday(configuredDate);
        } else {
            // Default: NEXT — move to the following Monday
            fallbackDate = getNextWeekday(configuredDate);
        }

        return today.equals(fallbackDate);
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private LocalDate getNextWeekday(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isWeekday(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private LocalDate getPreviousWeekday(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isWeekday(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }
}
