package com.ngxbot.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class NgnFormatUtil {

    private NgnFormatUtil() {
    }

    private static final NumberFormat NGN_FORMAT;

    static {
        NGN_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "NG"));
    }

    public static String formatNaira(BigDecimal amount) {
        if (amount == null) {
            return "N0.00";
        }
        return "N" + String.format("%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static String formatPercentage(BigDecimal pct) {
        if (pct == null) {
            return "0.00%";
        }
        return pct.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    public static String formatQuantity(int quantity) {
        return String.format("%,d", quantity);
    }
}
