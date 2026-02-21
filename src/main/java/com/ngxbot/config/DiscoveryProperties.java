package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "discovery")
public class DiscoveryProperties {

    private boolean enabled = true;
    private int maxActiveWatchlistSize = 30;
    private int maxObservationSlots = 20;
    private int observationPeriodDays = 14;
    private int demotionCooldownDays = 90;
    private int noSignalDemotionDays = 60;
    private double minFundamentalScore = 50.0;
    private Screener screener = new Screener();

    @Data
    public static class Screener {
        private double minMarketCapMillions = 10.0;
        private int minAvgDailyVolume = 10000;
        private double minEps = 0.0;
        private double maxPeRatio = 30.0;
        private double minRevenueGrowthPct = 5.0;
        private double maxDebtToEquity = 2.0;
    }
}
