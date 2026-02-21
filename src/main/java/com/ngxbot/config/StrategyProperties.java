package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "strategies")
public class StrategyProperties {
    private EtfNavArbitrage etfNavArbitrage = new EtfNavArbitrage();
    private MomentumBreakout momentumBreakout = new MomentumBreakout();
    private PensionFlow pensionFlow = new PensionFlow();
    private UsEarningsMomentum usEarningsMomentum = new UsEarningsMomentum();
    private UsEtfRotation usEtfRotation = new UsEtfRotation();
    private DividendAccumulation dividendAccumulation = new DividendAccumulation();
    private ValueAccumulation valueAccumulation = new ValueAccumulation();
    private Dca dca = new Dca();
    private CurrencyHedge currencyHedge = new CurrencyHedge();
    private SectorRotation sectorRotation = new SectorRotation();

    @Data
    public static class EtfNavArbitrage {
        private boolean enabled = true;
        private BigDecimal entryDiscountPct = new BigDecimal("10.0");
        private BigDecimal exitPremiumPct = new BigDecimal("20.0");
        private BigDecimal extremePremiumPct = new BigDecimal("50.0");
        private int maxRsi = 60;
        private BigDecimal minVolumeRatio = new BigDecimal("1.2");
    }

    @Data
    public static class MomentumBreakout {
        private boolean enabled = true;
        private BigDecimal volumeSpikeRatio = new BigDecimal("3.0");
        private int minRsi = 40;
        private int maxRsi = 65;
        private int smaPeriod = 20;
        private BigDecimal atrStopMultiplier = new BigDecimal("2.0");
        private BigDecimal atrTargetMultiplier = new BigDecimal("4.0");
    }

    @Data
    public static class PensionFlow {
        private boolean enabled = true;
    }

    @Data
    public static class UsEarningsMomentum {
        private boolean enabled = false;
        private int buyWindowDaysAfterEarnings = 3;
        private BigDecimal minEpsSurprisePct = new BigDecimal("5.0");
        private BigDecimal minRevenueSurprisePct = new BigDecimal("2.0");
        private int maxRsi = 70;
        private long minAvgVolume = 100_000L;
        private BigDecimal atrStopMultiplier = new BigDecimal("1.5");
        private BigDecimal atrTargetMultiplier = new BigDecimal("2.0");
        private BigDecimal maxTargetPct = new BigDecimal("0.10");
        private int baseConfidence = 70;
    }

    @Data
    public static class UsEtfRotation {
        private boolean enabled = false;
        private String rotationFrequency = "MONTHLY";
        private int topNSectors = 3;
        private BigDecimal minScore = new BigDecimal("60");
        private BigDecimal targetPct = new BigDecimal("0.05");
        private BigDecimal stopPct = new BigDecimal("0.05");
        private BigDecimal oneMonthWeight = new BigDecimal("0.40");
        private BigDecimal threeMonthWeight = new BigDecimal("0.30");
        private BigDecimal rsiWeight = new BigDecimal("0.15");
        private BigDecimal volumeWeight = new BigDecimal("0.15");
        private int maxDayOfMonth = 3;
    }

    @Data
    public static class DividendAccumulation {
        private boolean enabled = true;
        private BigDecimal ngxMinTrailingYieldPct = new BigDecimal("6.0");
        private BigDecimal usMinTrailingYieldPct = new BigDecimal("2.5");
    }

    @Data
    public static class ValueAccumulation {
        private boolean enabled = true;
        private int minFundamentalScore = 50;
    }

    @Data
    public static class Dca {
        private boolean enabled = true;
        private int ngxExecutionDay = 5;
        private int usExecutionDay = 10;
        private BigDecimal ngxMonthlyBudget = new BigDecimal("150000");
        private BigDecimal usMonthlyBudget = new BigDecimal("300");
    }

    @Data
    public static class CurrencyHedge {
        private boolean enabled = true;
        private BigDecimal goldTargetPct = new BigDecimal("10.0");
        private BigDecimal nairaWeaknessThreshold30dPct = new BigDecimal("5.0");
    }

    @Data
    public static class SectorRotation {
        private boolean enabled = true;
    }
}
