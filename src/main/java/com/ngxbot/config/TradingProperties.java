package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {
    private String timezone = "Africa/Lagos";
    private String marketOpen = "10:00";
    private String marketClose = "14:30";
    private BigDecimal dailyPriceLimitPct = new BigDecimal("10.0");
    private Watchlist watchlist = new Watchlist();

    @Data
    public static class Watchlist {
        private List<String> etfs = List.of();
        private List<String> largeCaps = List.of();
    }
}
