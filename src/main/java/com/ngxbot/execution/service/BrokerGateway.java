package com.ngxbot.execution.service;

import com.ngxbot.execution.entity.TradeOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for broker interactions. Implemented by TroveBrowserAgent (Playwright)
 * and future TroveApiClient.
 */
public interface BrokerGateway {

    /** Submit a limit order to the broker. Returns broker-assigned order ID. */
    String submitOrder(TradeOrder order) throws Exception;

    /** Check order status on the broker platform. */
    String checkOrderStatus(String orderId) throws Exception;

    /** Get current portfolio holdings from broker. Returns symbol -> quantity map. */
    Map<String, Integer> getPortfolioHoldings() throws Exception;

    /** Get available cash balance from broker by market. */
    BigDecimal getAvailableCash(String market) throws Exception;

    /** Get current FX rate displayed by broker (USD/NGN). */
    BigDecimal getBrokerFxRate() throws Exception;

    /** Login to broker platform. */
    void login() throws Exception;

    /** Check if session is still active. */
    boolean isSessionActive();

    /** Take a screenshot and return the file path. */
    String takeScreenshot(String context) throws Exception;
}
