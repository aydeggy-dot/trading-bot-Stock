package com.ngxbot.risk.service;

import com.ngxbot.strategy.StrategyMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains per-market, per-currency ledger of settling vs available cash.
 * <p>
 * NGX settlement cycle is T+2 (cash from sales is available 2 business days later).
 * US settlement cycle is T+1 (cash from sales is available 1 business day later).
 * <p>
 * Thread-safe via {@link ConcurrentHashMap} for the ledger and synchronized
 * mutation methods for individual market entries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCashTracker {

    /**
     * Immutable snapshot of a market's cash position.
     */
    public record CashLedger(BigDecimal totalCash, BigDecimal availableCash,
                              BigDecimal settlingCash, String currency) {
    }

    /**
     * Internal mutable entry tracking settling amounts with their settlement dates.
     */
    private static class MutableLedger {
        BigDecimal availableCash;
        final List<SettlingEntry> settlingEntries;
        final String currency;

        MutableLedger(BigDecimal availableCash, String currency) {
            this.availableCash = availableCash;
            this.settlingEntries = new ArrayList<>();
            this.currency = currency;
        }

        synchronized BigDecimal getSettlingCash() {
            return settlingEntries.stream()
                    .map(e -> e.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        synchronized BigDecimal getTotalCash() {
            return availableCash.add(getSettlingCash());
        }

        synchronized CashLedger toSnapshot() {
            BigDecimal settling = getSettlingCash();
            return new CashLedger(availableCash.add(settling), availableCash, settling, currency);
        }
    }

    private record SettlingEntry(BigDecimal amount, LocalDate settlementDate) {
    }

    private final ConcurrentHashMap<StrategyMarket, MutableLedger> ledgers = new ConcurrentHashMap<>();

    /**
     * Returns ONLY cash that has cleared settlement for the given market.
     *
     * @param market the strategy market (NGX, US, or BOTH)
     * @return available (settled) cash
     */
    public BigDecimal getAvailableCash(StrategyMarket market) {
        MutableLedger ledger = ledgers.get(resolveMarket(market));
        if (ledger == null) {
            log.warn("No cash ledger initialized for market {}. Returning ZERO.", market);
            return BigDecimal.ZERO;
        }
        synchronized (ledger) {
            return ledger.availableCash;
        }
    }

    /**
     * Returns cash currently in settlement (not yet available) for the given market.
     */
    public BigDecimal getSettlingCash(StrategyMarket market) {
        MutableLedger ledger = ledgers.get(resolveMarket(market));
        if (ledger == null) {
            return BigDecimal.ZERO;
        }
        return ledger.getSettlingCash();
    }

    /**
     * Returns total cash (available + settling) for the given market.
     */
    public BigDecimal getTotalCash(StrategyMarket market) {
        MutableLedger ledger = ledgers.get(resolveMarket(market));
        if (ledger == null) {
            return BigDecimal.ZERO;
        }
        return ledger.getTotalCash();
    }

    /**
     * Records a sale. The proceeds enter the settling pool and become available
     * after T+2 (NGX) or T+1 (US) business days.
     *
     * @param market   market of the sale
     * @param amount   sale proceeds
     * @param saleDate date of the sale
     */
    public void recordSale(StrategyMarket market, BigDecimal amount, LocalDate saleDate) {
        StrategyMarket resolved = resolveMarket(market);
        MutableLedger ledger = ledgers.get(resolved);
        if (ledger == null) {
            throw new IllegalStateException("Cash ledger not initialized for market " + resolved);
        }

        int settlementDays = resolved == StrategyMarket.NGX ? 2 : 1;
        LocalDate settlementDate = addBusinessDays(saleDate, settlementDays);

        synchronized (ledger) {
            ledger.settlingEntries.add(new SettlingEntry(amount, settlementDate));
        }

        log.info("Recorded sale of {} on {} for market {}. Settles on {} (T+{})",
                amount.toPlainString(), saleDate, resolved, settlementDate, settlementDays);
    }

    /**
     * Processes settlements: moves all entries whose settlement date has arrived
     * (or passed) from the settling pool into available cash.
     *
     * @param today the current business date
     */
    public void processSettlements(LocalDate today) {
        for (Map.Entry<StrategyMarket, MutableLedger> entry : ledgers.entrySet()) {
            StrategyMarket market = entry.getKey();
            MutableLedger ledger = entry.getValue();

            synchronized (ledger) {
                List<SettlingEntry> settled = ledger.settlingEntries.stream()
                        .filter(e -> !e.settlementDate.isAfter(today))
                        .toList();

                if (!settled.isEmpty()) {
                    BigDecimal settledAmount = settled.stream()
                            .map(e -> e.amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    ledger.availableCash = ledger.availableCash.add(settledAmount);
                    ledger.settlingEntries.removeAll(settled);

                    log.info("Settled {} for market {}. Available cash now: {}",
                            settledAmount.toPlainString(), market, ledger.availableCash.toPlainString());
                }
            }
        }
    }

    /**
     * Records a purchase, deducting from available (settled) cash.
     *
     * @param market market of the purchase
     * @param amount purchase cost
     * @throws IllegalStateException if insufficient available cash
     */
    public void recordPurchase(StrategyMarket market, BigDecimal amount) {
        StrategyMarket resolved = resolveMarket(market);
        MutableLedger ledger = ledgers.get(resolved);
        if (ledger == null) {
            throw new IllegalStateException("Cash ledger not initialized for market " + resolved);
        }

        synchronized (ledger) {
            if (ledger.availableCash.compareTo(amount) < 0) {
                throw new IllegalStateException(String.format(
                        "Insufficient available cash for market %s. Available: %s, Required: %s",
                        resolved, ledger.availableCash.toPlainString(), amount.toPlainString()));
            }
            ledger.availableCash = ledger.availableCash.subtract(amount);
        }

        log.info("Recorded purchase of {} for market {}. Available cash now: {}",
                amount.toPlainString(), resolved, ledger.availableCash.toPlainString());
    }

    /**
     * Initializes (or resets) the available cash for a market. Used during bootstrap.
     *
     * @param market the strategy market
     * @param amount initial available cash
     */
    public void initializeCash(StrategyMarket market, BigDecimal amount) {
        StrategyMarket resolved = resolveMarket(market);
        String currency = resolved == StrategyMarket.NGX ? "NGN" : "USD";
        ledgers.put(resolved, new MutableLedger(amount, currency));
        log.info("Initialized cash ledger for market {} with {} {}", resolved, amount.toPlainString(), currency);
    }

    /**
     * Returns an immutable snapshot of the cash ledger for a given market.
     */
    public CashLedger getLedgerSnapshot(StrategyMarket market) {
        MutableLedger ledger = ledgers.get(resolveMarket(market));
        if (ledger == null) {
            String currency = resolveMarket(market) == StrategyMarket.NGX ? "NGN" : "USD";
            return new CashLedger(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, currency);
        }
        return ledger.toSnapshot();
    }

    /**
     * Scheduled daily at 09:00 WAT on weekdays to process any pending settlements.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Africa/Lagos")
    public void dailySettlementProcessing() {
        LocalDate today = LocalDate.now();
        log.info("Running daily settlement processing for {}", today);
        processSettlements(today);
    }

    // ---- Private helpers ----

    /**
     * Resolves BOTH to NGX for settlement purposes (caller should invoke for each market separately).
     */
    private StrategyMarket resolveMarket(StrategyMarket market) {
        if (market == StrategyMarket.BOTH) {
            return StrategyMarket.NGX;
        }
        return market;
    }

    /**
     * Adds the given number of business days (skipping weekends) to a date.
     */
    private LocalDate addBusinessDays(LocalDate date, int days) {
        LocalDate result = date;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            DayOfWeek dow = result.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }
}
