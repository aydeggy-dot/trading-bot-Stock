package com.ngxbot.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Service
public class AiFallbackHandler {

    private static final double HIGH_FALLBACK_THRESHOLD = 0.5;

    private final AtomicInteger totalCallCount = new AtomicInteger(0);
    private final AtomicInteger fallbackCount = new AtomicInteger(0);

    public <T> Optional<T> executeWithFallback(Supplier<Optional<T>> aiCall, String operationName) {
        totalCallCount.incrementAndGet();

        try {
            Optional<T> result = aiCall.get();

            if (result.isEmpty()) {
                recordFallback(operationName, "Empty result from AI call");
                return Optional.empty();
            }

            return result;

        } catch (Exception e) {
            recordFallback(operationName, e.getMessage());
            return Optional.empty();
        }
    }

    public double getFallbackRate() {
        int total = totalCallCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) fallbackCount.get() / total;
    }

    public boolean isHighFallbackRate() {
        return getFallbackRate() > HIGH_FALLBACK_THRESHOLD;
    }

    public int getTotalCallCount() {
        return totalCallCount.get();
    }

    public int getFallbackCount() {
        return fallbackCount.get();
    }

    private void recordFallback(String operationName, String reason) {
        int currentFallbacks = fallbackCount.incrementAndGet();
        int total = totalCallCount.get();
        double rate = (double) currentFallbacks / total;

        log.warn("AI fallback triggered for operation='{}': reason='{}' (fallbacks={}/{})",
                operationName, reason, currentFallbacks, total);

        if (rate > HIGH_FALLBACK_THRESHOLD && total >= 4) {
            log.error("AI fallback rate is critically high: {}/{} ({}%) - " +
                            "AI service may be degraded. Trading decisions will use rule-based logic only.",
                    currentFallbacks, total, String.format("%.1f", rate * 100));
        }
    }
}
