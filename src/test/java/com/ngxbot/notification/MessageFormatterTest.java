package com.ngxbot.notification;

import com.ngxbot.notification.service.MessageFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest {

    private MessageFormatter messageFormatter;

    @BeforeEach
    void setUp() {
        messageFormatter = new MessageFormatter();
    }

    // ---- formatMoney ----

    @Test
    @DisplayName("formatMoney returns Naira symbol with comma formatting for NGN")
    void formatMoney_ngn() {
        String result = messageFormatter.formatMoney(new BigDecimal("1234.56"), "NGN");

        assertThat(result).isEqualTo("\u20A61,234.56");
    }

    @Test
    @DisplayName("formatMoney returns dollar symbol with comma formatting for USD")
    void formatMoney_usd() {
        String result = messageFormatter.formatMoney(new BigDecimal("1234.56"), "USD");

        assertThat(result).isEqualTo("$1,234.56");
    }

    @Test
    @DisplayName("formatMoney returns zero amount with proper formatting")
    void formatMoney_zeroAmount() {
        String result = messageFormatter.formatMoney(BigDecimal.ZERO, "NGN");

        assertThat(result).isEqualTo("\u20A60.00");
    }

    // ---- formatTradeAlert ----

    @Test
    @DisplayName("formatTradeAlert contains side, symbol, quantity, and price")
    void formatTradeAlert_containsKeyInfo() {
        String result = messageFormatter.formatTradeAlert(
                "BUY", "ZENITHBANK", 500,
                new BigDecimal("35.50"), "NGN",
                new BigDecimal("0.015")
        );

        assertThat(result).contains("BUY");
        assertThat(result).contains("ZENITHBANK");
        assertThat(result).contains("500");
        assertThat(result).contains("35.50");
    }

    // ---- formatApprovalRequest ----

    @Test
    @DisplayName("formatApprovalRequest contains YES, NO, and timeout information")
    void formatApprovalRequest_containsYesNo() {
        String result = messageFormatter.formatApprovalRequest(
                "BUY", "GTCO", 200,
                new BigDecimal("42.00"), "NGN",
                new BigDecimal("0.018"), 5
        );

        assertThat(result).contains("YES");
        assertThat(result).contains("NO");
        assertThat(result).containsIgnoringCase("reject");
    }

    // ---- formatNewsAlert ----

    @Test
    @DisplayName("formatNewsAlert contains source and symbol")
    void formatNewsAlert_containsSourceAndSymbol() {
        String result = messageFormatter.formatNewsAlert(
                "BusinessDay", "DANGCEM", "Dangote Cement posts record Q3 earnings"
        );

        assertThat(result).contains("BusinessDay");
        assertThat(result).contains("DANGCEM");
    }

    // ---- formatCircuitBreakerAlert ----

    @Test
    @DisplayName("formatCircuitBreakerAlert with crossMarket=true contains CROSS-MARKET")
    void formatCircuitBreakerAlert_crossMarket() {
        String result = messageFormatter.formatCircuitBreakerAlert(
                new BigDecimal("0.055"), true
        );

        assertThat(result).containsIgnoringCase("CROSS-MARKET");
    }

    // ---- formatSettlementConfirmation ----

    @Test
    @DisplayName("formatSettlementConfirmation contains formatted amount and symbol")
    void formatSettlementConfirmation_containsAmount() {
        String result = messageFormatter.formatSettlementConfirmation(
                new BigDecimal("175000.00"), "NGN", "ZENITHBANK"
        );

        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).contains("175,000.00"),
                r -> assertThat(r).contains("175000")
        );
        assertThat(result).contains("ZENITHBANK");
    }
}
