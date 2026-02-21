package com.ngxbot.ai.client;

import com.ngxbot.ai.entity.AiCostLedger;
import com.ngxbot.ai.repository.AiCostLedgerRepository;
import com.ngxbot.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiCostTracker {

    // Anthropic pricing per 1 million tokens
    private static final BigDecimal HAIKU_INPUT_PER_MILLION = new BigDecimal("0.80");
    private static final BigDecimal HAIKU_OUTPUT_PER_MILLION = new BigDecimal("4.00");
    private static final BigDecimal SONNET_INPUT_PER_MILLION = new BigDecimal("3.00");
    private static final BigDecimal SONNET_OUTPUT_PER_MILLION = new BigDecimal("15.00");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final AiProperties aiProperties;
    private final AiCostLedgerRepository costLedgerRepository;
    private final ConcurrentHashMap<LocalDate, BigDecimal> dailyTotals = new ConcurrentHashMap<>();

    public AiCostTracker(AiProperties aiProperties,
                         AiCostLedgerRepository costLedgerRepository) {
        this.aiProperties = aiProperties;
        this.costLedgerRepository = costLedgerRepository;
    }

    public BigDecimal recordCost(String model, int inputTokens, int outputTokens) {
        return recordCost(model, inputTokens, outputTokens, null);
    }

    public BigDecimal recordCost(String model, int inputTokens, int outputTokens, String purpose) {
        BigDecimal cost = calculateCost(model, inputTokens, outputTokens);
        LocalDate today = LocalDate.now();

        // Update in-memory daily total
        dailyTotals.merge(today, cost, BigDecimal::add);

        // Persist to database
        AiCostLedger ledger = AiCostLedger.builder()
                .callDate(today)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .costUsd(cost)
                .purpose(purpose)
                .build();
        costLedgerRepository.save(ledger);

        log.debug("Recorded AI cost: model={}, inputTokens={}, outputTokens={}, cost=${}",
                model, inputTokens, outputTokens, cost);

        return cost;
    }

    public BigDecimal getDailyCost(LocalDate date) {
        // Check in-memory cache first for today
        if (date.equals(LocalDate.now())) {
            BigDecimal cached = dailyTotals.get(date);
            if (cached != null) {
                return cached;
            }
        }
        // Fall back to database
        return costLedgerRepository.sumCostByDate(date);
    }

    public BigDecimal getMonthlyCost(YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        return costLedgerRepository.sumCostByDateRange(startDate, endDate);
    }

    public boolean isBudgetExceeded() {
        LocalDate today = LocalDate.now();

        // Check daily limit
        BigDecimal dailyCost = getDailyCost(today);
        if (dailyCost.compareTo(aiProperties.getBudget().getDailyLimitUsd()) >= 0) {
            log.warn("AI daily budget exceeded: ${} >= ${}", dailyCost,
                    aiProperties.getBudget().getDailyLimitUsd());
            return true;
        }

        // Check monthly limit
        BigDecimal monthlyCost = getMonthlyCost(YearMonth.from(today));
        if (monthlyCost.compareTo(aiProperties.getBudget().getMonthlyLimitUsd()) >= 0) {
            log.warn("AI monthly budget exceeded: ${} >= ${}", monthlyCost,
                    aiProperties.getBudget().getMonthlyLimitUsd());
            return true;
        }

        return false;
    }

    public boolean canAffordCall() {
        return !isBudgetExceeded();
    }

    private BigDecimal calculateCost(String model, int inputTokens, int outputTokens) {
        BigDecimal inputRate;
        BigDecimal outputRate;

        if (model != null && model.toLowerCase().contains("sonnet")) {
            inputRate = SONNET_INPUT_PER_MILLION;
            outputRate = SONNET_OUTPUT_PER_MILLION;
        } else {
            // Default to Haiku pricing
            inputRate = HAIKU_INPUT_PER_MILLION;
            outputRate = HAIKU_OUTPUT_PER_MILLION;
        }

        BigDecimal inputCost = inputRate
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = outputRate
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }
}
