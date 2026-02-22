package com.ngxbot.longterm.service;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.DividendEvent;
import com.ngxbot.longterm.repository.DividendEventRepository;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Auto-reinvests received dividends by purchasing additional shares of the
 * same stock (or a configured alternative).
 * <p>
 * Uses the net amount received (after withholding tax for US) to calculate
 * the number of whole shares that can be purchased at the current price.
 * The reinvestment target is controlled by
 * {@link LongtermProperties.Dividend#getReinvestInto()} (default: "SAME_STOCK").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendReinvestmentService {

    private final DividendEventRepository dividendEventRepository;
    private final PositionRepository positionRepository;
    private final LongtermProperties longtermProperties;

    /**
     * Finds all unreinvested dividend events and processes each one.
     * <p>
     * For each dividend event where reinvested=false:
     * <ol>
     *   <li>Determines the reinvestment target symbol</li>
     *   <li>Gets the current price for that symbol</li>
     *   <li>Calculates purchasable shares = floor(netAmountReceived / currentPrice)</li>
     *   <li>Logs the reinvestment details</li>
     *   <li>Marks the dividend event as reinvested</li>
     * </ol>
     */
    @Transactional
    public void processReinvestments() {
        if (!longtermProperties.getDividend().isReinvest()) {
            log.info("Dividend reinvestment is disabled. Skipping.");
            return;
        }

        List<DividendEvent> unreinvestedDividends = dividendEventRepository.findByReinvestedFalse();

        if (unreinvestedDividends.isEmpty()) {
            log.info("No unreinvested dividends found. Nothing to process.");
            return;
        }

        log.info("Processing {} unreinvested dividend events", unreinvestedDividends.size());

        int successCount = 0;
        int skipCount = 0;

        for (DividendEvent event : unreinvestedDividends) {
            try {
                boolean processed = reinvestDividend(event);
                if (processed) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                log.error("Failed to reinvest dividend for {} (id={}): {}",
                        event.getSymbol(), event.getId(), e.getMessage(), e);
                skipCount++;
            }
        }

        log.info("Dividend reinvestment complete. Processed: {}, Skipped: {}",
                successCount, skipCount);
    }

    /**
     * Executes reinvestment for a single dividend event.
     * <p>
     * Determines the target symbol (same stock by default or as configured),
     * retrieves the current price, calculates purchasable shares, and marks
     * the event as reinvested.
     *
     * @param event the dividend event to reinvest
     * @return true if reinvestment was executed, false if skipped
     */
    @Transactional
    public boolean reinvestDividend(DividendEvent event) {
        BigDecimal netAmount = event.getNetAmountReceived();

        if (netAmount == null || netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Dividend event {} for {} has zero or null net amount. Skipping reinvestment.",
                    event.getId(), event.getSymbol());
            return false;
        }

        // Determine reinvestment target
        String reinvestInto = longtermProperties.getDividend().getReinvestInto();
        String targetSymbol;

        if ("SAME_STOCK".equals(reinvestInto) || reinvestInto == null || reinvestInto.isBlank()) {
            targetSymbol = event.getSymbol();
        } else {
            targetSymbol = reinvestInto;
        }

        // Get current price for the target symbol
        BigDecimal currentPrice = getCurrentPrice(targetSymbol);

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No valid current price for target symbol {}. "
                    + "Cannot reinvest dividend for {} (id={}). Will retry later.",
                    targetSymbol, event.getSymbol(), event.getId());
            return false;
        }

        // Calculate shares: floor(netAmount / currentPrice)
        int shares = netAmount.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();

        if (shares <= 0) {
            log.info("Net dividend amount {} {} is insufficient for even 1 share of {} at {}. "
                            + "Marking as reinvested with 0 shares.",
                    netAmount.toPlainString(), event.getCurrency(),
                    targetSymbol, currentPrice.toPlainString());
            // Mark as reinvested even if no shares purchased (amount too small)
            markAsReinvested(event, null);
            return true;
        }

        BigDecimal reinvestmentAmount = currentPrice.multiply(BigDecimal.valueOf(shares))
                .setScale(2, RoundingMode.HALF_UP);

        // Generate a reinvestment order ID
        String reinvestOrderId = "DRIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Reinvesting dividend for {} -- target: {}, net amount: {} {}, "
                        + "current price: {}, shares: {}, reinvestment amount: {}, order: {}",
                event.getSymbol(), targetSymbol,
                netAmount.toPlainString(), event.getCurrency(),
                currentPrice.toPlainString(), shares,
                reinvestmentAmount.toPlainString(), reinvestOrderId);

        // Mark the dividend event as reinvested
        markAsReinvested(event, reinvestOrderId);

        return true;
    }

    /**
     * Marks a dividend event as reinvested and persists the update.
     *
     * @param event          the dividend event
     * @param reinvestOrderId the order ID (null if no shares purchased)
     */
    private void markAsReinvested(DividendEvent event, String reinvestOrderId) {
        event.setReinvested(true);
        event.setReinvestOrderId(reinvestOrderId);
        dividendEventRepository.save(event);

        log.debug("Marked dividend event {} for {} as reinvested (order: {})",
                event.getId(), event.getSymbol(),
                reinvestOrderId != null ? reinvestOrderId : "N/A");
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
