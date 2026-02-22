package com.ngxbot.notification.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats notification messages for trade alerts, approvals, circuit breakers,
 * monthly reports, and other bot events.
 * <p>
 * All monetary values use {@link BigDecimal}. Percentages are stored as decimals
 * (e.g. 0.02 = 2%) and converted to display format internally.
 */
@Component
public class MessageFormatter {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Formats a monetary amount with the appropriate currency symbol.
     *
     * @param amount   the amount
     * @param currency "NGN" or "USD"
     * @return formatted string, e.g. "₦1,234.56" or "$1,234.56"
     */
    public String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        String formatted = String.format("%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
        return switch (currency != null ? currency.toUpperCase() : "NGN") {
            case "USD" -> "$" + formatted;
            default -> "\u20A6" + formatted; // ₦
        };
    }

    /**
     * Formats a trade alert notification.
     *
     * @param side     "BUY" or "SELL"
     * @param symbol   stock ticker
     * @param quantity number of shares
     * @param price    limit price per share
     * @param currency "NGN" or "USD"
     * @param riskPct  portfolio risk as decimal (0.02 = 2%)
     * @return formatted trade alert message
     */
    public String formatTradeAlert(String side, String symbol, int quantity,
                                   BigDecimal price, String currency,
                                   BigDecimal riskPct) {
        BigDecimal totalValue = price.multiply(BigDecimal.valueOf(quantity));
        String riskDisplay = formatPct(riskPct);

        return String.format("""
                        *TRADE ALERT*
                        %s %s
                        Qty: %,d shares @ %s
                        Total: %s
                        Risk: %s of portfolio
                        Time: %s WAT""",
                side.toUpperCase(),
                symbol,
                quantity,
                formatMoney(price, currency),
                formatMoney(totalValue, currency),
                riskDisplay,
                LocalDateTime.now().format(TIMESTAMP_FMT));
    }

    /**
     * Formats a trade-approval request with YES/NO instructions.
     *
     * @param side           "BUY" or "SELL"
     * @param symbol         stock ticker
     * @param quantity       number of shares
     * @param price          limit price per share
     * @param currency       "NGN" or "USD"
     * @param riskPct        portfolio risk as decimal (0.02 = 2%)
     * @param timeoutMinutes minutes before the approval times out
     * @return formatted approval request
     */
    public String formatApprovalRequest(String side, String symbol, int quantity,
                                        BigDecimal price, String currency,
                                        BigDecimal riskPct, int timeoutMinutes) {
        BigDecimal totalValue = price.multiply(BigDecimal.valueOf(quantity));
        String riskDisplay = formatPct(riskPct);

        return String.format("""
                        *TRADE APPROVAL REQUIRED*
                        %s %s
                        Qty: %,d shares @ %s
                        Total: %s
                        Risk: %s of portfolio

                        Reply *YES* to approve or *NO* to reject.
                        Auto-%s in %d minute(s) if no response.""",
                side.toUpperCase(),
                symbol,
                quantity,
                formatMoney(price, currency),
                formatMoney(totalValue, currency),
                riskDisplay,
                "REJECT".equalsIgnoreCase(getDefaultWord()) ? "reject" : "approve",
                timeoutMinutes);
    }

    /**
     * Formats a news alert.
     *
     * @param source   news source (e.g. "Nairametrics", "BusinessDay")
     * @param symbol   related stock ticker
     * @param headline the news headline
     * @return formatted news alert
     */
    public String formatNewsAlert(String source, String symbol, String headline) {
        return String.format("""
                        *NEWS ALERT*
                        Source: %s
                        Symbol: %s
                        %s
                        Time: %s WAT""",
                source,
                symbol,
                headline,
                LocalDateTime.now().format(TIMESTAMP_FMT));
    }

    /**
     * Formats a circuit-breaker alert.
     *
     * @param dailyLossPct daily loss as decimal (e.g. 0.05 = 5%)
     * @param crossMarket  true if the breaker spans multiple markets
     * @return formatted circuit breaker alert
     */
    public String formatCircuitBreakerAlert(BigDecimal dailyLossPct, boolean crossMarket) {
        String scope = crossMarket ? "CROSS-MARKET" : "SINGLE-MARKET";
        String lossDisplay = formatPct(dailyLossPct);

        return String.format("""
                        *CIRCUIT BREAKER TRIGGERED*
                        Scope: %s
                        Daily Loss: %s
                        Status: Trading HALTED

                        Manual review required before resuming.
                        Time: %s WAT""",
                scope,
                lossDisplay,
                LocalDateTime.now().format(TIMESTAMP_FMT));
    }

    /**
     * Formats a settlement confirmation.
     *
     * @param amount   settled amount
     * @param currency "NGN" or "USD"
     * @param symbol   settled stock ticker
     * @return formatted settlement confirmation
     */
    public String formatSettlementConfirmation(BigDecimal amount, String currency, String symbol) {
        return String.format("""
                        *SETTLEMENT CONFIRMATION*
                        Symbol: %s
                        Amount: %s
                        Status: Settled (T+2)
                        Time: %s WAT""",
                symbol,
                formatMoney(amount, currency),
                LocalDateTime.now().format(TIMESTAMP_FMT));
    }

    /**
     * Formats a monthly portfolio report.
     *
     * @param ngxValue      NGX portfolio value
     * @param usValue       US portfolio value
     * @param totalNgn      total value in NGN
     * @param monthlyReturn monthly return as decimal (e.g. 0.035 = 3.5%)
     * @param ytdReturn     year-to-date return as decimal
     * @return formatted monthly report
     */
    public String formatMonthlyReport(BigDecimal ngxValue, BigDecimal usValue,
                                      BigDecimal totalNgn, BigDecimal monthlyReturn,
                                      BigDecimal ytdReturn) {
        return String.format("""
                        *MONTHLY PORTFOLIO REPORT*

                        NGX Value: %s
                        US Value: %s
                        Total (NGN): %s

                        Monthly Return: %s
                        YTD Return: %s

                        Generated: %s WAT""",
                formatMoney(ngxValue, "NGN"),
                formatMoney(usValue, "USD"),
                formatMoney(totalNgn, "NGN"),
                formatPct(monthlyReturn),
                formatPct(ytdReturn),
                LocalDateTime.now().format(TIMESTAMP_FMT));
    }

    // ---- internal helpers ----

    /**
     * Converts a decimal percentage (0.02) to display format ("2.00%").
     */
    private String formatPct(BigDecimal decimal) {
        if (decimal == null) {
            return "0.00%";
        }
        BigDecimal pct = decimal.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    /**
     * Returns a placeholder default-on-timeout word; the actual value comes
     * from NotificationProperties at runtime, but this formatter is stateless
     * so we use "REJECT" as the safe default.
     */
    private String getDefaultWord() {
        return "REJECT";
    }
}
