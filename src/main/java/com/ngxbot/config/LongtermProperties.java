package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "longterm")
public class LongtermProperties {
    private Core core = new Core();
    private Dca dca = new Dca();
    private Dividend dividend = new Dividend();
    private Rebalance rebalance = new Rebalance();

    @Data
    public static class Core {
        private Map<String, BigDecimal> targetAllocations = new LinkedHashMap<>();
    }

    @Data
    public static class Dca {
        private boolean enabled = true;
        private BigDecimal ngxBudgetNairaMonthly = new BigDecimal("150000");
        private BigDecimal usBudgetUsdMonthly = new BigDecimal("300");
        private int ngxExecutionDay = 5;
        private int usExecutionDay = 10;
        private String fallbackDayIfWeekend = "NEXT";
        private BigDecimal topUpOnDipPct = new BigDecimal("10.0");
    }

    @Data
    public static class Dividend {
        private boolean reinvest = true;
        private String reinvestInto = "SAME_STOCK";
        private boolean trackExDates = true;
        private int alertDaysBeforeExDate = 7;
        private BigDecimal usWithholdingTaxPct = new BigDecimal("30.0");
    }

    @Data
    public static class Rebalance {
        private String frequency = "QUARTERLY";
        private BigDecimal driftThresholdPct = new BigDecimal("10.0");
        private String method = "THRESHOLD";
        private boolean useNewCashFirst = true;
        private boolean requireApproval = true;
    }
}
