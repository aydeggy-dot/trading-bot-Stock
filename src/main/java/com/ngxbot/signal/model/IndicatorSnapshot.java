package com.ngxbot.signal.model;

import java.math.BigDecimal;

public record IndicatorSnapshot(
    BigDecimal rsi14,
    BigDecimal macdLine,
    BigDecimal macdSignal,
    BigDecimal macdHistogram,
    BigDecimal sma20,
    BigDecimal ema12,
    BigDecimal ema26,
    BigDecimal atr14,
    BigDecimal volumeRatio,
    BigDecimal obv,
    BigDecimal currentPrice,
    Long currentVolume,
    Long avgVolume20d
) {}
