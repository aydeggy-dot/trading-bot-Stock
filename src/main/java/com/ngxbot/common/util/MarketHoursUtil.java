package com.ngxbot.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.ngxbot.config.TradingProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class MarketHoursUtil {

    private final TradingProperties tradingProperties;

    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(getTimezone());
        return isMarketOpen(now);
    }

    public boolean isMarketOpen(ZonedDateTime dateTime) {
        ZonedDateTime watTime = dateTime.withZoneSameInstant(getTimezone());
        DayOfWeek day = watTime.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = watTime.toLocalTime();
        LocalTime open = LocalTime.parse(tradingProperties.getMarketOpen());
        LocalTime close = LocalTime.parse(tradingProperties.getMarketClose());

        return !time.isBefore(open) && !time.isAfter(close);
    }

    public boolean isTradingDay() {
        ZonedDateTime now = ZonedDateTime.now(getTimezone());
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public ZoneId getTimezone() {
        return ZoneId.of(tradingProperties.getTimezone());
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(getTimezone());
    }
}
