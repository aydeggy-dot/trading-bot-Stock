package com.ngxbot.longterm;

import com.ngxbot.config.LongtermProperties;
import com.ngxbot.longterm.entity.DcaPlan;
import com.ngxbot.longterm.repository.DcaPlanRepository;
import com.ngxbot.longterm.service.DcaExecutor;
import com.ngxbot.longterm.service.DcaExecutor.DcaAllocation;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.strategy.StrategyMarket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DcaExecutorTest {

    @Mock private DcaPlanRepository dcaPlanRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private SettlementCashTracker settlementCashTracker;
    @Mock private LongtermProperties longtermProperties;
    @Mock private LongtermProperties.Dca dcaProperties;

    private DcaExecutor dcaExecutor;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 5);

    @BeforeEach
    void setUp() {
        dcaExecutor = new DcaExecutor(dcaPlanRepository, positionRepository,
                settlementCashTracker, longtermProperties);
    }

    // ---- Helpers ----

    private DcaPlan ngxPlan(String symbol, BigDecimal weightPct, boolean active) {
        return DcaPlan.builder()
                .symbol(symbol)
                .market("NGX")
                .currency("NGN")
                .weightPct(weightPct)
                .isActive(active)
                .totalInvested(BigDecimal.ZERO)
                .build();
    }

    private DcaPlan usPlan(String symbol, BigDecimal weightPct, boolean active) {
        return DcaPlan.builder()
                .symbol(symbol)
                .market("US")
                .currency("USD")
                .weightPct(weightPct)
                .isActive(active)
                .totalInvested(BigDecimal.ZERO)
                .build();
    }

    private Position openPosition(String symbol, BigDecimal currentPrice) {
        return Position.builder()
                .symbol(symbol)
                .currentPrice(currentPrice)
                .isOpen(true)
                .quantity(100)
                .avgEntryPrice(currentPrice)
                .entryDate(TEST_DATE.minusDays(30))
                .build();
    }

    // ---- Tests ----

    @Test
    @DisplayName("executeNgxDca allocates budget across 2 NGX plans with 60/40 weight split")
    void executeNgxDca_allocatesBudgetAcrossPlans() {
        // Budget = 150,000 NGN, available cash = 200,000 NGN
        when(longtermProperties.getDca()).thenReturn(dcaProperties);
        when(dcaProperties.isEnabled()).thenReturn(true);
        when(dcaProperties.getNgxBudgetNairaMonthly()).thenReturn(new BigDecimal("150000"));

        DcaPlan zenith = ngxPlan("ZENITHBANK", new BigDecimal("60.00"), true);
        DcaPlan gtco = ngxPlan("GTCO", new BigDecimal("40.00"), true);

        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX")).thenReturn(List.of(zenith, gtco));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX)).thenReturn(new BigDecimal("200000"));

        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(positionRepository.findBySymbolAndIsOpenTrue("GTCO"))
                .thenReturn(List.of(openPosition("GTCO", new BigDecimal("42.00"))));

        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.executeNgxDca(TEST_DATE);

        assertThat(allocations).hasSize(2);

        // Zenith gets 60% of 150,000 = 90,000 NGN -> 90000/35.50 = 2535 shares
        DcaAllocation zenithAlloc = allocations.stream()
                .filter(a -> "ZENITHBANK".equals(a.symbol())).findFirst().orElseThrow();
        assertThat(zenithAlloc.quantity()).isEqualTo(2535);
        assertThat(zenithAlloc.currency()).isEqualTo("NGN");
        assertThat(zenithAlloc.market()).isEqualTo("NGX");

        // GTCO gets 40% of 150,000 = 60,000 NGN -> 60000/42.00 = 1428 shares
        DcaAllocation gtcoAlloc = allocations.stream()
                .filter(a -> "GTCO".equals(a.symbol())).findFirst().orElseThrow();
        assertThat(gtcoAlloc.quantity()).isEqualTo(1428);
    }

    @Test
    @DisplayName("executeUsDca allocates budget across 2 US plans")
    void executeUsDca_allocatesBudgetAcrossPlans() {
        // Budget = $300 USD, available cash = $500 USD
        when(longtermProperties.getDca()).thenReturn(dcaProperties);
        when(dcaProperties.isEnabled()).thenReturn(true);
        when(dcaProperties.getUsBudgetUsdMonthly()).thenReturn(new BigDecimal("300"));

        DcaPlan voo = usPlan("VOO", new BigDecimal("50.00"), true);
        DcaPlan schd = usPlan("SCHD", new BigDecimal("50.00"), true);

        when(dcaPlanRepository.findByMarketAndIsActiveTrue("US")).thenReturn(List.of(voo, schd));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.US)).thenReturn(new BigDecimal("500"));

        when(positionRepository.findBySymbolAndIsOpenTrue("VOO"))
                .thenReturn(List.of(openPosition("VOO", new BigDecimal("450.00"))));
        when(positionRepository.findBySymbolAndIsOpenTrue("SCHD"))
                .thenReturn(List.of(openPosition("SCHD", new BigDecimal("25.00"))));

        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.executeUsDca(TEST_DATE);

        // VOO: 50% of $300 = $150 / $450 = 0 shares -> skipped
        // SCHD: 50% of $300 = $150 / $25 = 6 shares
        assertThat(allocations).hasSize(1);

        DcaAllocation schdAlloc = allocations.get(0);
        assertThat(schdAlloc.symbol()).isEqualTo("SCHD");
        assertThat(schdAlloc.quantity()).isEqualTo(6);
        assertThat(schdAlloc.currency()).isEqualTo("USD");
        assertThat(schdAlloc.market()).isEqualTo("US");
    }

    @Test
    @DisplayName("allocateBudget caps total allocated to available cash when budget exceeds it")
    void allocateBudget_capsToAvailableCash() {
        // Budget = 150,000 but only 50,000 available
        DcaPlan plan = ngxPlan("ZENITHBANK", new BigDecimal("100.00"), true);
        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX")).thenReturn(List.of(plan));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX)).thenReturn(new BigDecimal("50000"));

        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.allocateBudget("NGX",
                new BigDecimal("150000"), TEST_DATE);

        assertThat(allocations).hasSize(1);
        // Total allocated should not exceed 50,000
        BigDecimal totalAllocated = allocations.stream()
                .map(DcaAllocation::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalAllocated).isLessThanOrEqualTo(new BigDecimal("50000"));

        // 50000/35.50 = 1408 shares * 35.50 = 49984.00
        assertThat(allocations.get(0).quantity()).isEqualTo(1408);
    }

    @Test
    @DisplayName("allocateBudget skips inactive plans and returns only active plan allocations")
    void allocateBudget_skipsInactivePlans() {
        DcaPlan active1 = ngxPlan("ZENITHBANK", new BigDecimal("50.00"), true);
        DcaPlan active2 = ngxPlan("GTCO", new BigDecimal("50.00"), true);
        DcaPlan inactive = ngxPlan("DANGCEM", new BigDecimal("30.00"), false);

        // findByMarketAndIsActiveTrue should only return active plans
        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX"))
                .thenReturn(List.of(active1, active2));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX))
                .thenReturn(new BigDecimal("200000"));

        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(positionRepository.findBySymbolAndIsOpenTrue("GTCO"))
                .thenReturn(List.of(openPosition("GTCO", new BigDecimal("42.00"))));

        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.allocateBudget("NGX",
                new BigDecimal("150000"), TEST_DATE);

        // Only 2 active plans should produce allocations (inactive plan is excluded by repo query)
        assertThat(allocations).hasSize(2);
        assertThat(allocations).extracting(DcaAllocation::symbol)
                .containsExactlyInAnyOrder("ZENITHBANK", "GTCO")
                .doesNotContain("DANGCEM");
    }

    @Test
    @DisplayName("allocateBudget skips symbol when position has zero price")
    void allocateBudget_handlesZeroPriceGracefully() {
        DcaPlan planWithPrice = ngxPlan("ZENITHBANK", new BigDecimal("50.00"), true);
        DcaPlan planNoPrice = ngxPlan("GTCO", new BigDecimal("50.00"), true);

        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX"))
                .thenReturn(List.of(planWithPrice, planNoPrice));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX))
                .thenReturn(new BigDecimal("200000"));

        // ZENITHBANK has a valid price; GTCO has price 0
        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(positionRepository.findBySymbolAndIsOpenTrue("GTCO"))
                .thenReturn(List.of(openPosition("GTCO", BigDecimal.ZERO)));

        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.allocateBudget("NGX",
                new BigDecimal("150000"), TEST_DATE);

        // Only ZENITHBANK should have an allocation; GTCO skipped due to zero price
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).symbol()).isEqualTo("ZENITHBANK");
    }

    @Test
    @DisplayName("calculateShares rounds down: 1000 / 35.50 = 28 shares (not 29)")
    void calculateShares_roundsDown() {
        // We test via allocateBudget with a single plan where budget = 1000, price = 35.50
        // 1000 / 35.50 = 28.169... should floor to 28
        DcaPlan plan = ngxPlan("ZENITHBANK", new BigDecimal("100.00"), true);
        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX")).thenReturn(List.of(plan));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX))
                .thenReturn(new BigDecimal("10000"));

        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.allocateBudget("NGX",
                new BigDecimal("1000"), TEST_DATE);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).quantity()).isEqualTo(28);
        // Total = 28 * 35.50 = 994.00 (not 29 * 35.50 = 1029.50)
        assertThat(allocations.get(0).totalAmount())
                .isEqualByComparingTo(new BigDecimal("994.00"));
    }

    @Test
    @DisplayName("allocateBudget updates lastExecutionDate on DcaPlan after allocation")
    void allocateBudget_updatesLastExecutionDate() {
        DcaPlan plan = ngxPlan("ZENITHBANK", new BigDecimal("100.00"), true);
        assertThat(plan.getLastExecutionDate()).isNull();

        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX")).thenReturn(List.of(plan));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX))
                .thenReturn(new BigDecimal("200000"));
        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        dcaExecutor.allocateBudget("NGX", new BigDecimal("150000"), TEST_DATE);

        // Capture saved plan and verify lastExecutionDate was set
        ArgumentCaptor<DcaPlan> captor = ArgumentCaptor.forClass(DcaPlan.class);
        verify(dcaPlanRepository).save(captor.capture());

        DcaPlan savedPlan = captor.getValue();
        assertThat(savedPlan.getLastExecutionDate()).isEqualTo(TEST_DATE);
    }

    @Test
    @DisplayName("allocateBudget increases totalInvested by the allocated amount")
    void allocateBudget_updatesTotalInvested() {
        DcaPlan plan = ngxPlan("ZENITHBANK", new BigDecimal("100.00"), true);
        BigDecimal initialInvested = plan.getTotalInvested(); // BigDecimal.ZERO

        when(dcaPlanRepository.findByMarketAndIsActiveTrue("NGX")).thenReturn(List.of(plan));
        when(settlementCashTracker.getAvailableCash(StrategyMarket.NGX))
                .thenReturn(new BigDecimal("200000"));
        when(positionRepository.findBySymbolAndIsOpenTrue("ZENITHBANK"))
                .thenReturn(List.of(openPosition("ZENITHBANK", new BigDecimal("35.50"))));
        when(dcaPlanRepository.save(any(DcaPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        List<DcaAllocation> allocations = dcaExecutor.allocateBudget("NGX",
                new BigDecimal("150000"), TEST_DATE);

        ArgumentCaptor<DcaPlan> captor = ArgumentCaptor.forClass(DcaPlan.class);
        verify(dcaPlanRepository).save(captor.capture());

        DcaPlan savedPlan = captor.getValue();
        BigDecimal expectedAmount = allocations.get(0).totalAmount();

        assertThat(savedPlan.getTotalInvested())
                .isEqualByComparingTo(initialInvested.add(expectedAmount));
        assertThat(savedPlan.getTotalInvested()).isGreaterThan(BigDecimal.ZERO);
    }
}
