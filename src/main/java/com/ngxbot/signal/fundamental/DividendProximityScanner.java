package com.ngxbot.signal.fundamental;

import com.ngxbot.data.entity.CorporateAction;
import com.ngxbot.data.repository.CorporateActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Scans for upcoming dividend ex-dates to adjust trading signals.
 * Stocks near dividend dates may have elevated prices (pre-ex-date rally)
 * or depressed prices (post-ex-date adjustment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendProximityScanner {

    private final CorporateActionRepository corporateActionRepository;

    /**
     * Check if a stock has an upcoming dividend within the next N days.
     */
    public boolean hasUpcomingDividend(String symbol, int withinDays) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(withinDays);

        List<CorporateAction> actions = corporateActionRepository
                .findBySymbolAndActionType(symbol, "DIVIDEND");

        return actions.stream()
                .anyMatch(a -> a.getExDate() != null
                        && !a.getExDate().isBefore(from)
                        && !a.getExDate().isAfter(to));
    }

    /**
     * Get all upcoming dividends across all stocks within a date range.
     */
    public List<CorporateAction> getUpcomingDividends(int withinDays) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(withinDays);
        return corporateActionRepository.findByExDateBetween(from, to);
    }

    /**
     * Check if we're in the typical bank dividend announcement season.
     * Nigerian banks typically announce dividends in Q1 (Feb-April).
     */
    public boolean isBankDividendSeason() {
        int month = LocalDate.now().getMonthValue();
        return month >= 2 && month <= 4;
    }
}
