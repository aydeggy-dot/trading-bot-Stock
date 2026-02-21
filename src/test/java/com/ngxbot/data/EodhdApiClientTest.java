package com.ngxbot.data;

import com.ngxbot.config.EodhdProperties;
import com.ngxbot.data.client.EodhdApiClient;
import com.ngxbot.data.entity.OhlcvBar;
import com.ngxbot.data.repository.OhlcvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EodhdApiClientTest {

    @Mock
    private OhlcvRepository ohlcvRepository;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private EodhdProperties eodhdProperties;
    private EodhdApiClient eodhdApiClient;

    @BeforeEach
    void setUp() {
        eodhdProperties = new EodhdProperties();
        eodhdProperties.setApiKey("test-api-key");
        eodhdProperties.setBaseUrl("https://eodhd.com/api");
        eodhdProperties.setExchange("XNSA");

        eodhdApiClient = new EodhdApiClient(webClient, eodhdProperties, ohlcvRepository);
    }

    @Test
    @DisplayName("Should fetch and store OHLCV data successfully")
    void fetchAndStoreOhlcv_success() {
        // Given
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate to = LocalDate.of(2026, 1, 12);

        EodhdApiClient.EodhdOhlcvResponse response1 = new EodhdApiClient.EodhdOhlcvResponse(
                "2026-01-10",
                new BigDecimal("25.50"),
                new BigDecimal("26.00"),
                new BigDecimal("25.00"),
                new BigDecimal("25.75"),
                new BigDecimal("25.75"),
                1234567L
        );
        EodhdApiClient.EodhdOhlcvResponse response2 = new EodhdApiClient.EodhdOhlcvResponse(
                "2026-01-11",
                new BigDecimal("25.75"),
                new BigDecimal("27.00"),
                new BigDecimal("25.50"),
                new BigDecimal("26.80"),
                new BigDecimal("26.80"),
                2345678L
        );

        // Mock WebClient chain
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(EodhdApiClient.EodhdOhlcvResponse.class))
                .thenReturn(Flux.just(response1, response2));

        // Mock repository - no existing bars (insert new)
        when(ohlcvRepository.findBySymbolAndTradeDate(eq("ZENITHBANK"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(ohlcvRepository.save(any(OhlcvBar.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OhlcvBar> result = eodhdApiClient.fetchAndStoreOhlcv("ZENITHBANK", from, to);

        // Then
        assertThat(result).hasSize(2);
        verify(ohlcvRepository, times(2)).save(any(OhlcvBar.class));

        ArgumentCaptor<OhlcvBar> captor = ArgumentCaptor.forClass(OhlcvBar.class);
        verify(ohlcvRepository, times(2)).save(captor.capture());

        List<OhlcvBar> saved = captor.getAllValues();
        assertThat(saved.get(0).getSymbol()).isEqualTo("ZENITHBANK");
        assertThat(saved.get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("25.75"));
        assertThat(saved.get(0).getVolume()).isEqualTo(1234567L);
        assertThat(saved.get(0).getDataSource()).isEqualTo("EODHD");

        assertThat(saved.get(1).getClosePrice()).isEqualByComparingTo(new BigDecimal("26.80"));
    }

    @Test
    @DisplayName("Should handle empty response from EODHD")
    void fetchAndStoreOhlcv_emptyResponse() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(EodhdApiClient.EodhdOhlcvResponse.class))
                .thenReturn(Flux.empty());

        // When
        List<OhlcvBar> result = eodhdApiClient.fetchAndStoreOhlcv("GTCO",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));

        // Then
        assertThat(result).isEmpty();
        verify(ohlcvRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle API error gracefully")
    void fetchAndStoreOhlcv_apiError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(EodhdApiClient.EodhdOhlcvResponse.class))
                .thenReturn(Flux.error(WebClientResponseException.create(
                        401, "Unauthorized", null, null, null)));

        // When
        List<OhlcvBar> result = eodhdApiClient.fetchAndStoreOhlcv("DANGCEM",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));

        // Then
        assertThat(result).isEmpty();
        verify(ohlcvRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update existing bar (upsert) instead of creating duplicate")
    void fetchAndStoreOhlcv_upsert() {
        // Given
        LocalDate tradeDate = LocalDate.of(2026, 1, 15);
        OhlcvBar existingBar = OhlcvBar.builder()
                .id(42L)
                .symbol("UBA")
                .tradeDate(tradeDate)
                .closePrice(new BigDecimal("10.00"))
                .dataSource("EODHD")
                .build();

        EodhdApiClient.EodhdOhlcvResponse response = new EodhdApiClient.EodhdOhlcvResponse(
                "2026-01-15",
                new BigDecimal("10.50"),
                new BigDecimal("11.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.80"),
                new BigDecimal("10.80"),
                500000L
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(EodhdApiClient.EodhdOhlcvResponse.class))
                .thenReturn(Flux.just(response));

        // Existing bar found — should update it
        when(ohlcvRepository.findBySymbolAndTradeDate("UBA", tradeDate))
                .thenReturn(Optional.of(existingBar));
        when(ohlcvRepository.save(any(OhlcvBar.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OhlcvBar> result = eodhdApiClient.fetchAndStoreOhlcv("UBA",
                tradeDate.minusDays(1), tradeDate);

        // Then
        assertThat(result).hasSize(1);
        ArgumentCaptor<OhlcvBar> captor = ArgumentCaptor.forClass(OhlcvBar.class);
        verify(ohlcvRepository).save(captor.capture());

        OhlcvBar saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(42L); // same entity updated
        assertThat(saved.getClosePrice()).isEqualByComparingTo(new BigDecimal("10.80")); // new price
    }

    @Test
    @DisplayName("Should fetch recent OHLCV using calculated date range")
    void fetchRecentOhlcv_delegatesToFetchAndStore() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(EodhdApiClient.EodhdOhlcvResponse.class))
                .thenReturn(Flux.empty());

        // When
        List<OhlcvBar> result = eodhdApiClient.fetchRecentOhlcv("ACCESSCORP", 30);

        // Then
        assertThat(result).isEmpty();
        // Verify WebClient was called (fetchRecentOhlcv delegates to fetchAndStoreOhlcv)
        verify(webClient).get();
    }

    @Test
    @DisplayName("Should fetch fundamentals successfully")
    void fetchFundamentals_success() {
        // Given
        String expectedJson = "{\"General\":{\"Name\":\"Zenith Bank\",\"Sector\":\"Financial Services\"}}";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(expectedJson));

        // When
        String result = eodhdApiClient.fetchFundamentals("ZENITHBANK");

        // Then
        assertThat(result).isEqualTo(expectedJson);
        assertThat(result).contains("Zenith Bank");
    }
}
