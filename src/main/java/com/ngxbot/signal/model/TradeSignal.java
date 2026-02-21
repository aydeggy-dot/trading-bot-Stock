package com.ngxbot.signal.model;

import com.ngxbot.common.model.TradeSide;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeSignal(
    String symbol,
    TradeSide side,
    BigDecimal suggestedPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    SignalStrength strength,
    int confidenceScore,
    String strategy,
    String reasoning,
    IndicatorSnapshot indicators,
    LocalDate signalDate
) {}
