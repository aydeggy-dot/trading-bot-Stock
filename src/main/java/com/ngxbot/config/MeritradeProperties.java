package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Data
@Component
@ConfigurationProperties(prefix = "meritrade")
public class MeritradeProperties {
    private String baseUrl = "https://web.meritrade.com";
    private String username;
    private String password;
    private boolean headless = true;
    private double slowMoMs = 500;
    private String screenshotDir = "./screenshots";
    private int sessionMaxHours = 5;
    private int orderConfirmationDelaySeconds = 3;
    private Map<String, String> selectors = new HashMap<>();
}
