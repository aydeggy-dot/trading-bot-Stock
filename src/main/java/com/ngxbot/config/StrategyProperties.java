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
}
