package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {
    private BigDecimal maxRiskPerTradePct = new BigDecimal("2.0");
    private BigDecimal maxSinglePositionPct = new BigDecimal("15.0");
    private BigDecimal maxSectorExposurePct = new BigDecimal("40.0");
    private BigDecimal minCashReservePct = new BigDecimal("20.0");
    private BigDecimal dailyLossCircuitBreakerPct = new BigDecimal("5.0");
    private BigDecimal weeklyLossCircuitBreakerPct = new BigDecimal("10.0");
    private int maxOpenPositions = 10;
    private long minAvgDailyVolume = 10000;
    private BigDecimal maxVolumeParticipationPct = new BigDecimal("10.0");
}
