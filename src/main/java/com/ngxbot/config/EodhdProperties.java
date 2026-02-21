package com.ngxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "eodhd")
public class EodhdProperties {
    private String apiKey;
    private String baseUrl = "https://eodhd.com/api";
    private String exchange = "XNSA";
}
