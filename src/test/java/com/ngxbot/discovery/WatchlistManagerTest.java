package com.ngxbot.discovery;

import com.ngxbot.common.model.WatchlistStatus;
import com.ngxbot.config.DiscoveryProperties;
import com.ngxbot.config.TradingProperties;
import com.ngxbot.data.entity.WatchlistStock;
import com.ngxbot.data.repository.WatchlistStockRepository;
import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.service.WatchlistManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistManagerTest {

    @Mock
    private WatchlistStockRepository watchlistStockRepository;
    @Mock
    private DiscoveredStockRepository discoveredStockRepository;
    @Mock
    private TradingProperties tradingProperties;
    @Mock
    private DiscoveryProperties discoveryProperties;

    private WatchlistManager watchlistManager;

    @BeforeEach
    void setUp() {
        watchlistManager = new WatchlistManager(
                watchlistStockRepository, discoveredStockRepository,
                tradingProperties, discoveryProperties
        );
    }

    private void setupSeedSymbols() {
        TradingProperties.Watchlist watchlist = new TradingProperties.Watchlist();
        watchlist.setEtfs(List.of("STANBICETF30", "VETGRIF30"));
        watchlist.setLargeCaps(List.of("ZENITHBANK", "GTCO", "DANGCEM"));
        when(tradingProperties.getWatchlist()).thenReturn(watchlist);
    }

    @Test
    void getActiveWatchlistIncludesSeedAndPromoted() {
        setupSeedSymbols();

        DiscoveredStock promoted = new DiscoveredStock();
        promoted.setSymbol("NEWSTOCK");
        when(discoveredStockRepository.findByStatus(WatchlistStatus.PROMOTED.name()))
                .thenReturn(List.of(promoted));

        WatchlistStock active = new WatchlistStock();
        active.setSymbol("ACTIVEX");
        when(watchlistStockRepository.findByStatus(WatchlistStatus.ACTIVE.name()))
                .thenReturn(List.of(active));

        List<String> result = watchlistManager.getActiveWatchlist();

        assertThat(result).contains("STANBICETF30", "VETGRIF30", "ZENITHBANK", "GTCO", "DANGCEM");
        assertThat(result).contains("NEWSTOCK");
        assertThat(result).contains("ACTIVEX");
    }

    @Test
    void hasCapacityReturnsFalseWhenFull() {
        setupSeedSymbols();
        when(discoveryProperties.getMaxActiveWatchlistSize()).thenReturn(5);
        when(discoveredStockRepository.findByStatus(WatchlistStatus.PROMOTED.name()))
                .thenReturn(List.of());
        when(watchlistStockRepository.findByStatus(WatchlistStatus.ACTIVE.name()))
                .thenReturn(List.of());

        // 5 seed symbols, capacity = 5
        assertThat(watchlistManager.hasCapacity()).isFalse();
    }

    @Test
    void hasCapacityReturnsTrueWhenRoom() {
        setupSeedSymbols();
        when(discoveryProperties.getMaxActiveWatchlistSize()).thenReturn(30);
        when(discoveredStockRepository.findByStatus(WatchlistStatus.PROMOTED.name()))
                .thenReturn(List.of());
        when(watchlistStockRepository.findByStatus(WatchlistStatus.ACTIVE.name()))
                .thenReturn(List.of());

        // 5 seed symbols, capacity = 30
        assertThat(watchlistManager.hasCapacity()).isTrue();
    }

    @Test
    void hasObservationSlotsRespectsLimit() {
        when(discoveryProperties.getMaxObservationSlots()).thenReturn(20);
        when(discoveredStockRepository.countByStatus(WatchlistStatus.OBSERVATION.name()))
                .thenReturn(20L);

        assertThat(watchlistManager.hasObservationSlots()).isFalse();
    }

    @Test
    void hasObservationSlotsAvailable() {
        when(discoveryProperties.getMaxObservationSlots()).thenReturn(20);
        when(discoveredStockRepository.countByStatus(WatchlistStatus.OBSERVATION.name()))
                .thenReturn(5L);

        assertThat(watchlistManager.hasObservationSlots()).isTrue();
    }

    @Test
    void isOnWatchlistChecksSeedSymbols() {
        setupSeedSymbols();

        assertThat(watchlistManager.isOnWatchlist("ZENITHBANK")).isTrue();
        assertThat(watchlistManager.isOnWatchlist("UNKNOWN")).isFalse();
    }

    @Test
    void isOnWatchlistChecksActiveDbStocks() {
        TradingProperties.Watchlist watchlist = new TradingProperties.Watchlist();
        watchlist.setEtfs(List.of());
        watchlist.setLargeCaps(List.of());
        when(tradingProperties.getWatchlist()).thenReturn(watchlist);

        WatchlistStock active = WatchlistStock.builder()
                .symbol("DBSTOCK")
                .status(WatchlistStatus.ACTIVE.name())
                .build();
        when(watchlistStockRepository.findBySymbol("DBSTOCK")).thenReturn(Optional.of(active));

        assertThat(watchlistManager.isOnWatchlist("DBSTOCK")).isTrue();
    }

    @Test
    void getObservationSymbolsReturnsCorrectList() {
        DiscoveredStock obs1 = new DiscoveredStock();
        obs1.setSymbol("OBS1");
        DiscoveredStock obs2 = new DiscoveredStock();
        obs2.setSymbol("OBS2");
        when(discoveredStockRepository.findByStatus(WatchlistStatus.OBSERVATION.name()))
                .thenReturn(List.of(obs1, obs2));

        assertThat(watchlistManager.getObservationSymbols()).containsExactly("OBS1", "OBS2");
    }
}
