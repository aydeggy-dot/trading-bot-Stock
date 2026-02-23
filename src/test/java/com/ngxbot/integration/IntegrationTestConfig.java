package com.ngxbot.integration;

import com.ngxbot.execution.entity.TradeOrder;
import com.ngxbot.execution.service.BrokerGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a stub BrokerGateway for integration tests that don't need
 * the real Playwright-based TroveBrowserAgent.
 *
 * This activates ONLY when:
 *   - Profile is "integration"
 *   - No other BrokerGateway bean exists (i.e., meritrade.enabled != true)
 */
@Configuration
@Profile("integration")
@Slf4j
public class IntegrationTestConfig {

    @Bean
    @ConditionalOnMissingBean(BrokerGateway.class)
    public BrokerGateway stubBrokerGateway() {
        log.info("[INTEGRATION-TEST] Using stub BrokerGateway — no real broker connection");
        return new StubBrokerGateway();
    }

    /**
     * Stub that returns plausible values for dashboard/reconciliation tests
     * but cannot submit real orders.
     */
    static class StubBrokerGateway implements BrokerGateway {

        @Override
        public void login() throws Exception {
            log.info("[STUB] login() called — no-op in stub mode");
        }

        @Override
        public String submitOrder(TradeOrder order) throws Exception {
            log.warn("[STUB] submitOrder() called — returning mock order ID");
            return "STUB-" + System.currentTimeMillis();
        }

        @Override
        public String checkOrderStatus(String orderId) throws Exception {
            log.info("[STUB] checkOrderStatus({}) — returning PENDING", orderId);
            return "PENDING";
        }

        @Override
        public Map<String, Integer> getPortfolioHoldings() throws Exception {
            log.info("[STUB] getPortfolioHoldings() — returning empty map");
            return new HashMap<>();
        }

        @Override
        public BigDecimal getAvailableCash(String market) throws Exception {
            log.info("[STUB] getAvailableCash({}) — returning 0", market);
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal getBrokerFxRate() throws Exception {
            log.info("[STUB] getBrokerFxRate() — returning 1500.00");
            return new BigDecimal("1500.00");
        }

        @Override
        public boolean isSessionActive() {
            return false;
        }

        @Override
        public String takeScreenshot(String context) throws Exception {
            log.info("[STUB] takeScreenshot({}) — no-op", context);
            return "stub-screenshot.png";
        }
    }
}
