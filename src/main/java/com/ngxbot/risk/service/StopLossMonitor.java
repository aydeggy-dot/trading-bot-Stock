package com.ngxbot.risk.service;

import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Monitors open positions against their stop-loss levels during market hours.
 * <p>
 * NGX hours: 10:00-14:30 WAT (checked every 5 minutes, 10-14 cron window).
 * US hours: 15:30-21:00 WAT (checked every 5 minutes, 15-21 cron window).
 * <p>
 * When a stop-loss is triggered, the position is flagged by setting its
 * stop-loss hit status. Actual order execution is handled by the execution layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopLossMonitor {

    private final PositionRepository positionRepository;
    private final OhlcvRepository ohlcvRepository;

    /**
     * Scheduled check during NGX market hours (10:00-14:30 WAT).
     * Runs every 5 minutes on weekdays.
     */
    @Scheduled(cron = "0 */5 10-14 * * MON-FRI", zone = "Africa/Lagos")
    public void monitorNgxStops() {
        log.debug("Running NGX stop-loss monitor check");
        List<Position> ngxPositions = positionRepository.findByIsOpenTrue().stream()
                .filter(p -> p.getSymbol() != null && p.getSymbol().endsWith(".XNSA"))
                .toList();
        monitorPositions(ngxPositions, "NGX");
    }

    /**
     * Scheduled check during US market hours (15:30-21:00 WAT = 9:30-16:00 ET).
     * Runs every 5 minutes on weekdays.
     */
    @Scheduled(cron = "0 */5 15-21 * * MON-FRI", zone = "Africa/Lagos")
    public void monitorUsStops() {
        log.debug("Running US stop-loss monitor check");
        List<Position> usPositions = positionRepository.findByIsOpenTrue().stream()
                .filter(p -> p.getSymbol() == null || !p.getSymbol().endsWith(".XNSA"))
                .toList();
        monitorPositions(usPositions, "US");
    }

    /**
     * Checks whether the current price of a position has hit or breached its stop-loss.
     *
     * @param position the position to check
     * @return true if the stop-loss has been triggered
     */
    public boolean checkStopLoss(Position position) {
        if (position.getStopLoss() == null) {
            return false;
        }

        BigDecimal currentPrice = resolveCurrentPrice(position);
        if (currentPrice == null) {
            log.debug("No current price available for {}. Skipping stop-loss check.", position.getSymbol());
            return false;
        }

        boolean triggered = currentPrice.compareTo(position.getStopLoss()) <= 0;

        if (triggered) {
            log.warn("STOP-LOSS TRIGGERED for {} | current={} <= stop={} | entry={} | quantity={} | strategy={}",
                    position.getSymbol(),
                    currentPrice.toPlainString(),
                    position.getStopLoss().toPlainString(),
                    position.getAvgEntryPrice().toPlainString(),
                    position.getQuantity(),
                    position.getStrategy());
        }

        return triggered;
    }

    // ---- Private helpers ----

    private void monitorPositions(List<Position> positions, String marketLabel) {
        if (positions.isEmpty()) {
            log.debug("No open {} positions to monitor for stop-loss", marketLabel);
            return;
        }

        int triggeredCount = 0;
        for (Position position : positions) {
            if (checkStopLoss(position)) {
                markForClosure(position);
                triggeredCount++;
            }
        }

        if (triggeredCount > 0) {
            log.warn("{} stop-loss trigger(s) detected across {} open {} positions",
                    triggeredCount, positions.size(), marketLabel);
        } else {
            log.debug("All {} {} positions within stop-loss limits", positions.size(), marketLabel);
        }
    }

    /**
     * Marks a position for closure by the execution layer.
     * Sets the current price to reflect the stop-loss level and persists the update.
     * The execution layer detects positions needing closure and submits sell orders.
     */
    private void markForClosure(Position position) {
        BigDecimal currentPrice = resolveCurrentPrice(position);
        if (currentPrice != null) {
            position.setCurrentPrice(currentPrice);
        }
        // Calculate unrealized P&L at stop
        if (position.getAvgEntryPrice() != null && currentPrice != null) {
            BigDecimal pnl = currentPrice.subtract(position.getAvgEntryPrice())
                    .multiply(BigDecimal.valueOf(position.getQuantity()));
            position.setUnrealizedPnl(pnl);
        }
        positionRepository.save(position);
        log.warn("Position {} marked for stop-loss closure. Symbol={}, Qty={}",
                position.getId(), position.getSymbol(), position.getQuantity());
    }

    /**
     * Resolves the current price for a position. Prefers the position's
     * currentPrice field; falls back to the latest OHLCV close.
     */
    private BigDecimal resolveCurrentPrice(Position position) {
        if (position.getCurrentPrice() != null && position.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            return position.getCurrentPrice();
        }

        // Fall back to latest OHLCV bar close price
        List<OhlcvBar> bars = ohlcvRepository.findLatestBySymbol(position.getSymbol(), 1);
        if (!bars.isEmpty()) {
            return bars.get(0).getClosePrice();
        }

        return null;
    }
}
