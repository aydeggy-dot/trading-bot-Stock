package com.ngxbot.discovery.client;

import com.ngxbot.config.EodhdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EodhdScreenerClient {

    private final WebClient eodhdWebClient;
    private final EodhdProperties eodhdProperties;

    public record ScreenerResult(
            String symbol,
            String name,
            String sector,
            BigDecimal marketCap,
            long avgVolume,
            BigDecimal eps,
            BigDecimal peRatio
    ) {}

    /**
     * Screens NGX stocks using the EODHD screener API.
     * Calls GET /screener with exchange=XNSA and basic filters.
     *
     * @return list of screener results, or empty list on error
     */
    public List<ScreenerResult> screenNgxStocks() {
        try {
            log.info("Running EODHD screener for exchange: {}", eodhdProperties.getExchange());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = eodhdWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/screener")
                            .queryParam("api_token", eodhdProperties.getApiKey())
                            .queryParam("sort", "market_capitalization.desc")
                            .queryParam("filters", buildFilters())
                            .queryParam("limit", 100)
                            .queryParam("offset", 0)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("data")) {
                log.warn("EODHD screener returned null or missing data field");
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

            List<ScreenerResult> results = data.stream()
                    .map(this::parseScreenerEntry)
                    .toList();

            log.info("EODHD screener returned {} results for {}", results.size(), eodhdProperties.getExchange());
            return results;

        } catch (Exception e) {
            log.error("Error calling EODHD screener API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String buildFilters() {
        return String.format(
                "[[\"exchange\",\"=\",\"%s\"]]",
                eodhdProperties.getExchange()
        );
    }

    private ScreenerResult parseScreenerEntry(Map<String, Object> entry) {
        String code = getStringValue(entry, "code", "");
        String name = getStringValue(entry, "name", "");
        String sector = getStringValue(entry, "sector", "Unknown");
        BigDecimal marketCap = getBigDecimalValue(entry, "market_capitalization");
        long avgVolume = getLongValue(entry, "avgvol_200d");
        BigDecimal eps = getBigDecimalValue(entry, "earnings_share");
        BigDecimal peRatio = getBigDecimalValue(entry, "pe_ratio");

        return new ScreenerResult(code, name, sector, marketCap, avgVolume, eps, peRatio);
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString().split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
