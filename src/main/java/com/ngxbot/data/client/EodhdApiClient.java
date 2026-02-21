package com.ngxbot.data.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ngxbot.config.EodhdProperties;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class EodhdApiClient {

    private final WebClient eodhdWebClient;
    private final EodhdProperties eodhdProperties;
    private final OhlcvRepository ohlcvRepository;

    public EodhdApiClient(@Qualifier("eodhdWebClient") WebClient eodhdWebClient,
                          EodhdProperties eodhdProperties,
                          OhlcvRepository ohlcvRepository) {
        this.eodhdWebClient = eodhdWebClient;
        this.eodhdProperties = eodhdProperties;
        this.ohlcvRepository = ohlcvRepository;
    }

    /**
     * Fetch OHLCV data for a symbol from EODHD API and persist to database.
     * Ticker format: SYMBOL.XNSA (e.g., ZENITHBANK.XNSA)
     *
     * @param symbol stock symbol without exchange suffix
     * @param from start date
     * @param to end date
     * @return list of persisted OhlcvBar entities
     */
    public List<OhlcvBar> fetchAndStoreOhlcv(String symbol, LocalDate from, LocalDate to) {
        String ticker = symbol + "." + eodhdProperties.getExchange();
        log.info("Fetching OHLCV from EODHD: {} from {} to {}", ticker, from, to);

        try {
            List<EodhdOhlcvResponse> responses = eodhdWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/eod/{ticker}")
                            .queryParam("api_token", eodhdProperties.getApiKey())
                            .queryParam("fmt", "json")
                            .queryParam("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .queryParam("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .build(ticker))
                    .retrieve()
                    .bodyToFlux(EodhdOhlcvResponse.class)
                    .collectList()
                    .block();

            if (responses == null || responses.isEmpty()) {
                log.warn("No OHLCV data returned from EODHD for {}", ticker);
                return Collections.emptyList();
            }

            log.info("Received {} OHLCV bars for {}", responses.size(), symbol);
            return responses.stream()
                    .map(r -> upsertOhlcvBar(symbol, r))
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("EODHD API error for {}: {} - {}", ticker, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch OHLCV for {}: {}", ticker, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch OHLCV data for the last N trading days.
     */
    public List<OhlcvBar> fetchRecentOhlcv(String symbol, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        return fetchAndStoreOhlcv(symbol, from, to);
    }

    /**
     * Fetch fundamentals for a symbol (sector, market cap, PE, etc.)
     * Returns raw JSON as a String for flexibility.
     */
    public String fetchFundamentals(String symbol) {
        String ticker = symbol + "." + eodhdProperties.getExchange();
        log.info("Fetching fundamentals from EODHD: {}", ticker);

        try {
            return eodhdWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fundamentals/{ticker}")
                            .queryParam("api_token", eodhdProperties.getApiKey())
                            .queryParam("fmt", "json")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("EODHD fundamentals error for {}: {} - {}", ticker, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch fundamentals for {}: {}", ticker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Upsert logic: update existing bar or insert new one.
     */
    private OhlcvBar upsertOhlcvBar(String symbol, EodhdOhlcvResponse response) {
        LocalDate tradeDate = LocalDate.parse(response.date());

        OhlcvBar bar = ohlcvRepository.findBySymbolAndTradeDate(symbol, tradeDate)
                .orElse(OhlcvBar.builder()
                        .symbol(symbol)
                        .tradeDate(tradeDate)
                        .dataSource("EODHD")
                        .createdAt(LocalDateTime.now())
                        .build());

        bar.setOpenPrice(response.open());
        bar.setHighPrice(response.high());
        bar.setLowPrice(response.low());
        bar.setClosePrice(response.close());
        bar.setAdjustedClose(response.adjustedClose());
        bar.setVolume(response.volume());

        return ohlcvRepository.save(bar);
    }

    /**
     * EODHD API response record for OHLCV data.
     * Maps JSON: { "date": "2026-01-15", "open": 25.50, "high": 26.00, "low": 25.00, "close": 25.75, "adjusted_close": 25.75, "volume": 1234567 }
     */
    public record EodhdOhlcvResponse(
            String date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            @JsonProperty("adjusted_close") BigDecimal adjustedClose,
            Long volume
    ) {}
}
