package com.ngxbot.longterm;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.CoreHolding;
import com.ngxbot.longterm.entity.RebalanceAction;
import com.ngxbot.longterm.repository.CoreHoldingRepository;
import com.ngxbot.longterm.repository.RebalanceActionRepository;
import com.ngxbot.longterm.service.PortfolioRebalancer;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.risk.service.SettlementCashTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioRebalancerTest {

    @Mock private CoreHoldingRepository coreHoldingRepository;
    @Mock private RebalanceActionRepository rebalanceActionRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private SettlementCashTracker settlementCashTracker;
    @Mock private LongtermProperties longtermProperties;
    @Mock private LongtermProperties.Rebalance rebalanceProperties;

    private PortfolioRebalancer portfolioRebalancer;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        portfolioRebalancer = new PortfolioRebalancer(coreHoldingRepository,
                rebalanceActionRepository, positionRepository,
                settlementCashTracker, longtermProperties);
    }

    // ---- Helpers ----

    private CoreHolding holding(String symbol, String market, BigDecimal targetPct,
                                 BigDecimal currentPct, BigDecimal marketValue, int shares) {
        return CoreHolding.builder()
                .symbol(symbol)
                .market(market)
                .currency("NGX".equals(market) ? "NGN" : "USD")
                .targetWeightPct(targetPct)
                .currentWeightPct(currentPct)
                .marketValue(marketValue)
                .sharesHeld(shares)
                .avgCostBasis(new BigDecimal("30.00"))
                .build();
    }

    private Position openPosition(String symbol, BigDecimal currentPrice) {
        return Position.builder()
                .symbol(symbol)
                .currentPrice(currentPrice)
                .isOpen(true)
                .quantity(100)
                .avgEntryPrice(currentPrice)
                .entryDate(TEST_DATE.minusDays(60))
                .build();
    }

    // ---- Tests ----

    @Test
    @DisplayName("calculateDrift returns +5.0 when holding is overweight (current=15%, target=10%)")
    void calculateDrift_overweight() {
        CoreHolding overweight = holding("ZENITHBANK", "NGX",
                new BigDecimal("10.00"), new BigDecimal("15.00"),
                new BigDecimal("150000"), 4000);

        BigDecimal drift = portfolioRebalancer.calculateDrift(overweight);

        // drift = 15 - 10 = +5.00
        assertThat(drift).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(drift).isPositive();
    }

    @Test
    @DisplayName("calculateDrift returns -7.0 when holding is underweight (current=8%, target=15%)")
    void calculateDrift_underweight() {
        CoreHolding underweight = holding("GTCO", "NGX",
                new BigDecimal("15.00"), new BigDecimal("8.00"),
                new BigDecimal("80000"), 2000);

        BigDecimal drift = portfolioRebalancer.calculateDrift(underweight);

        // drift = 8 - 15 = -7.00
        assertThat(drift).isEqualByComparingTo(new BigDecimal("-7.00"));
        assertThat(drift).isNegative();
    }

    @Test
    @DisplayName("checkAndRebalance generates SELL action when drift exceeds threshold")
    void checkAndRebalance_generatesActionsWhenDriftExceedsThreshold() {
        // Threshold = 10%, holding at 25% with target 10% => drift = +15% > 10%
        when(longtermProperties.getRebalance()).thenReturn(rebalanceProperties);
        when(rebalanceProperties.getDriftThresholdPct()).thenReturn(new BigDecimal("10.0"));
        when(rebalanceProperties.isRequireApproval()).thenReturn(true);

        CoreHolding overweight = holding("ZENITHBANK", "NGX",
                new BigDecimal("10.00"), new BigDecimal("25.00"),
                new BigDecimal("250000"), 6000);

        // Total portfolio value comes from all holdings' market values
        when(coreHoldingRepository.findAll()).thenReturn(List.of(overweight));

        // Mock current price for quantity calculation
        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("40.00"))));

        when(rebalanceActionRepository.save(any(RebalanceAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<RebalanceAction> actions = portfolioRebalancer.checkAndRebalance(TEST_DATE);

        assertThat(actions).hasSize(1);
        RebalanceAction action = actions.get(0);
        assertThat(action.getActionType()).isEqualTo("SELL");
        assertThat(action.getSymbol()).isEqualTo("ZENITHBANK");
        assertThat(action.getDriftPct()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(action.getTriggerDate()).isEqualTo(TEST_DATE);
        assertThat(action.getStatus()).isEqualTo("PENDING");
        assertThat(action.getQuantity()).isGreaterThan(0);
    }

    @Test
    @DisplayName("checkAndRebalance generates no actions when all holdings within threshold")
    void checkAndRebalance_noActionsWhenWithinThreshold() {
        // Threshold = 10%; holding at 12% with target 10% => drift = +2% < 10%
        when(longtermProperties.getRebalance()).thenReturn(rebalanceProperties);
        when(rebalanceProperties.getDriftThresholdPct()).thenReturn(new BigDecimal("10.0"));

        CoreHolding withinThreshold1 = holding("ZENITHBANK", "NGX",
                new BigDecimal("10.00"), new BigDecimal("12.00"),
                new BigDecimal("120000"), 3000);
        CoreHolding withinThreshold2 = holding("GTCO", "NGX",
                new BigDecimal("15.00"), new BigDecimal("13.00"),
                new BigDecimal("130000"), 3500);

        when(coreHoldingRepository.findAll()).thenReturn(List.of(withinThreshold1, withinThreshold2));

        List<RebalanceAction> actions = portfolioRebalancer.checkAndRebalance(TEST_DATE);

        assertThat(actions).isEmpty();
        verify(rebalanceActionRepository, never()).save(any(RebalanceAction.class));
    }

    @Test
    @DisplayName("generateRebalanceActions creates BUY action for underweight holding")
    void generateRebalanceActions_createsBuyForUnderweight() {
        // Threshold = 5%, holding at 3% with target 15% => drift = -12% > 5%
        when(longtermProperties.getRebalance()).thenReturn(rebalanceProperties);
        when(rebalanceProperties.getDriftThresholdPct()).thenReturn(new BigDecimal("5.0"));
        when(rebalanceProperties.isRequireApproval()).thenReturn(false);
        when(rebalanceProperties.isUseNewCashFirst()).thenReturn(false);

        CoreHolding underweight = holding("GTCO", "NGX",
                new BigDecimal("15.00"), new BigDecimal("3.00"),
                new BigDecimal("30000"), 800);

        when(coreHoldingRepository.findAll()).thenReturn(List.of(underweight));
        when(positionRepository.findBySymbolAndIsOpenTrue("GTCO"))
                .thenReturn(List.of(openPosition("GTCO", new BigDecimal("38.00"))));

        when(rebalanceActionRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        List<RebalanceAction> actions = portfolioRebalancer.generateRebalanceActions(TEST_DATE);

        assertThat(actions).hasSize(1);
        RebalanceAction action = actions.get(0);
        assertThat(action.getActionType()).isEqualTo("BUY");
        assertThat(action.getSymbol()).isEqualTo("GTCO");
        assertThat(action.getDriftPct()).isEqualByComparingTo(new BigDecimal("-12.00"));
        assertThat(action.getStatus()).isEqualTo("APPROVED");
        assertThat(action.getQuantity()).isGreaterThan(0);
    }

    @Test
    @DisplayName("generateRebalanceActions creates SELL action for overweight holding")
    void generateRebalanceActions_createsSellForOverweight() {
        // Threshold = 5%, holding at 28% with target 10% => drift = +18% > 5%
        when(longtermProperties.getRebalance()).thenReturn(rebalanceProperties);
        when(rebalanceProperties.getDriftThresholdPct()).thenReturn(new BigDecimal("5.0"));
        when(rebalanceProperties.isRequireApproval()).thenReturn(true);
        when(rebalanceProperties.isUseNewCashFirst()).thenReturn(false);

        CoreHolding overweight = holding("DANGCEM", "NGX",
                new BigDecimal("10.00"), new BigDecimal("28.00"),
                new BigDecimal("280000"), 7000);

        when(coreHoldingRepository.findAll()).thenReturn(List.of(overweight));
        when(positionRepository.findBySymbolAndIsOpenTrue("DANGCEM"))
                .thenReturn(List.of(openPosition("DANGCEM", new BigDecimal("350.00"))));

        when(rebalanceActionRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        List<RebalanceAction> actions = portfolioRebalancer.generateRebalanceActions(TEST_DATE);

        assertThat(actions).hasSize(1);
        RebalanceAction action = actions.get(0);
        assertThat(action.getActionType()).isEqualTo("SELL");
        assertThat(action.getSymbol()).isEqualTo("DANGCEM");
        assertThat(action.getDriftPct()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(action.getStatus()).isEqualTo("PENDING");
        assertThat(action.getQuantity()).isGreaterThan(0);
    }

    @Test
    @DisplayName("executeApprovedActions updates status to EXECUTED and sets executedAt timestamp")
    void executeApprovedActions_updatesStatus() {
        RebalanceAction approvedAction = RebalanceAction.builder()
                .id(1L)
                .triggerDate(TEST_DATE)
                .symbol("ZENITHBANK")
                .market("NGX")
                .actionType("SELL")
                .currentWeightPct(new BigDecimal("25.00"))
                .targetWeightPct(new BigDecimal("10.00"))
                .driftPct(new BigDecimal("15.00"))
                .quantity(500)
                .estimatedValue(new BigDecimal("20000.00"))
                .status("APPROVED")
                .build();

        when(rebalanceActionRepository.findByStatus("APPROVED"))
                .thenReturn(List.of(approvedAction));
        when(rebalanceActionRepository.save(any(RebalanceAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(coreHoldingRepository.findBySymbolAndMarket("ZENITHBANK", "NGX"))
                .thenReturn(Optional.of(holding("ZENITHBANK", "NGX",
                        new BigDecimal("10.00"), new BigDecimal("25.00"),
                        new BigDecimal("250000"), 6000)));

        portfolioRebalancer.executeApprovedActions();

        ArgumentCaptor<RebalanceAction> captor = ArgumentCaptor.forClass(RebalanceAction.class);
        verify(rebalanceActionRepository).save(captor.capture());

        RebalanceAction executed = captor.getValue();
        assertThat(executed.getStatus()).isEqualTo("EXECUTED");
        assertThat(executed.getExecutedAt()).isNotNull();

        // Also verify the core holding's lastRebalanceDate is updated
        ArgumentCaptor<CoreHolding> holdingCaptor = ArgumentCaptor.forClass(CoreHolding.class);
        verify(coreHoldingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getLastRebalanceDate()).isEqualTo(LocalDate.now());
    }
}
