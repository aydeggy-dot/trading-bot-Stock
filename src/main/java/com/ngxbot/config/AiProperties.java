package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = false;
    private String apiKey;
    private String baseUrl = "https://api.anthropic.com";
    private String defaultModel = "claude-haiku-4-5-20251001";
    private String deepAnalysisModel = "claude-sonnet-4-6";
    private int maxRetries = 2;
    private int timeoutSeconds = 30;
    private Budget budget = new Budget();
    private List<String> deepAnalysisTriggers = List.of(
            "EARNINGS_RELEASE",
            "ACQUISITION_MERGER",
            "CBN_POLICY"
    );

    @Data
    public static class Budget {
        private BigDecimal dailyLimitUsd = new BigDecimal("5.00");
        private BigDecimal monthlyLimitUsd = new BigDecimal("100.00");
    }
}
