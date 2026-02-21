package com.ngxbot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    RiskProperties.class,
    TradingProperties.class,
    MeritradeProperties.class,
    NotificationProperties.class,
    EodhdProperties.class,
    StrategyProperties.class
})
public class AppConfig {
}
