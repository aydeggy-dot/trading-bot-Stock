package com.ngxbot.execution.service;

import com.ngxbot.execution.entity.TradeOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * STUB: Future API client for Trove brokerage.
 * Activates when trove.api.enabled=true.
 * When API access is available, this replaces TroveBrowserAgent
 * with zero architecture changes needed (same BrokerGateway interface).
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "trove.api.enabled", havingValue = "true", matchIfMissing = false)
public class TroveApiClient implements BrokerGateway {

    @Override
    public String submitOrder(TradeOrder order) throws Exception {
        throw new UnsupportedOperationException("Trove API not yet available — use Playwright agent");
    }

    @Override
    public String checkOrderStatus(String orderId) throws Exception {
        throw new UnsupportedOperationException("Trove API not yet available");
    }

    @Override
    public Map<String, Integer> getPortfolioHoldings() throws Exception {
        throw new UnsupportedOperationException("Trove API not yet available");
    }

    @Override
    public BigDecimal getAvailableCash(String market) throws Exception {
        throw new UnsupportedOperationException("Trove API not yet available");
    }

    @Override
    public BigDecimal getBrokerFxRate() throws Exception {
        throw new UnsupportedOperationException("Trove API not yet available");
    }

    @Override
    public void login() throws Exception {
        log.info("[TROVE-API] API login — not yet implemented");
    }

    @Override
    public boolean isSessionActive() {
        return false;
    }

    @Override
    public String takeScreenshot(String context) throws Exception {
        return "N/A — API mode";
    }
}
