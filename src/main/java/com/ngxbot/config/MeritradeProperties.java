package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;
import java.util.HashMap;

@Data
@ConfigurationProperties(prefix = "meritrade")
public class MeritradeProperties {
    private String baseUrl = "https://app.trovefinance.com/login";
    private String username;
    private String password;
    private boolean headless = true;
    private double slowMoMs = 500;
    private String screenshotDir = "./screenshots";
    private int sessionMaxHours = 5;
    private int orderConfirmationDelaySeconds = 3;
    /** Playwright navigation timeout in milliseconds (page.navigate, waitForLoadState). */
    private int navigationTimeoutMs = 30000;
    /** Screenshots older than this many hours are deleted on each login. 0 = no cleanup. */
    private int screenshotRetentionHours = 72;
    private Map<String, String> selectors = new HashMap<>();
}
