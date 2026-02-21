package com.ngxbot.news.classifier;

import com.ngxbot.common.model.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Maps each news-related {@link EventType} to an {@link ImpactRule} that
 * describes the base impact score, default sentiment, and confidence modifier.
 * Used by downstream scoring services to translate classified events into
 * actionable signal adjustments.
 */
@Slf4j
@Component
public class EventImpactRules {

    /**
     * Describes the impact characteristics of a single event type.
     *
     * @param eventType          the classified event type
     * @param baseImpactScore    the raw importance score (0-100)
     * @param defaultSentiment   default sentiment direction: POSITIVE, NEGATIVE, or NEUTRAL
     * @param confidenceModifier multiplier applied to the signal confidence (0.0 = kill signal, >1.0 = amplify)
     */
    public static record ImpactRule(
            EventType eventType,
            int baseImpactScore,
            String defaultSentiment,
            double confidenceModifier
    ) {}

    private static final Map<EventType, ImpactRule> RULES;

    static {
        Map<EventType, ImpactRule> map = new EnumMap<>(EventType.class);

        map.put(EventType.EARNINGS_RELEASE,
                new ImpactRule(EventType.EARNINGS_RELEASE, 80, "NEUTRAL", 1.2));
        map.put(EventType.DIVIDEND_ANNOUNCEMENT,
                new ImpactRule(EventType.DIVIDEND_ANNOUNCEMENT, 70, "POSITIVE", 1.1));
        map.put(EventType.RIGHTS_ISSUE,
                new ImpactRule(EventType.RIGHTS_ISSUE, 60, "NEGATIVE", 0.9));
        map.put(EventType.STOCK_SPLIT,
                new ImpactRule(EventType.STOCK_SPLIT, 40, "POSITIVE", 1.0));
        map.put(EventType.BONUS_ISSUE,
                new ImpactRule(EventType.BONUS_ISSUE, 50, "POSITIVE", 1.05));
        map.put(EventType.ACQUISITION_MERGER,
                new ImpactRule(EventType.ACQUISITION_MERGER, 90, "NEUTRAL", 1.3));
        map.put(EventType.REGULATORY_ACTION,
                new ImpactRule(EventType.REGULATORY_ACTION, 75, "NEGATIVE", 0.8));
        map.put(EventType.MANAGEMENT_CHANGE,
                new ImpactRule(EventType.MANAGEMENT_CHANGE, 65, "NEUTRAL", 0.9));
        map.put(EventType.INSIDER_TRADE,
                new ImpactRule(EventType.INSIDER_TRADE, 85, "NEUTRAL", 1.2));
        map.put(EventType.CREDIT_RATING_CHANGE,
                new ImpactRule(EventType.CREDIT_RATING_CHANGE, 70, "NEUTRAL", 1.1));
        map.put(EventType.SECTOR_NEWS,
                new ImpactRule(EventType.SECTOR_NEWS, 30, "NEUTRAL", 1.0));
        map.put(EventType.CBN_POLICY,
                new ImpactRule(EventType.CBN_POLICY, 95, "NEUTRAL", 1.4));
        map.put(EventType.TRADING_SUSPENSION,
                new ImpactRule(EventType.TRADING_SUSPENSION, 100, "NEGATIVE", 0.0));
        map.put(EventType.DELISTING_NOTICE,
                new ImpactRule(EventType.DELISTING_NOTICE, 100, "NEGATIVE", 0.0));
        map.put(EventType.AGM_EGM_NOTICE,
                new ImpactRule(EventType.AGM_EGM_NOTICE, 20, "NEUTRAL", 1.0));
        map.put(EventType.SHARE_BUYBACK,
                new ImpactRule(EventType.SHARE_BUYBACK, 60, "POSITIVE", 1.1));

        RULES = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the impact rule for a given event type.
     *
     * @param type the event type to look up
     * @return the matching impact rule, or empty if no rule is defined
     */
    public Optional<ImpactRule> getImpactRule(EventType type) {
        return Optional.ofNullable(RULES.get(type));
    }

    /**
     * Calculates the aggregate impact score for a list of classified events.
     * <p>
     * The aggregation takes the maximum base impact score among all events,
     * then applies a diminishing additive bonus for each additional event
     * (sorted descending by score). The result is clamped to [0, 100].
     * <p>
     * If any event has a confidence modifier of 0.0 (e.g., TRADING_SUSPENSION
     * or DELISTING_NOTICE), the aggregate score is immediately set to 100 to
     * indicate that any trading signal should be killed.
     *
     * @param events the list of classified event types
     * @return aggregate impact score between 0 and 100
     */
    public int calculateImpact(List<EventType> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        // Collect impact rules for all recognized events
        List<ImpactRule> matchedRules = new ArrayList<>();
        for (EventType event : events) {
            ImpactRule rule = RULES.get(event);
            if (rule != null) {
                // If any event kills the signal, return maximum impact immediately
                if (rule.confidenceModifier() == 0.0) {
                    log.info("Signal-killing event detected: {} — returning max impact 100", event);
                    return 100;
                }
                matchedRules.add(rule);
            }
        }

        if (matchedRules.isEmpty()) {
            return 0;
        }

        // Sort by base impact descending
        matchedRules.sort(Comparator.comparingInt(ImpactRule::baseImpactScore).reversed());

        // Start with the highest-impact event
        double aggregated = matchedRules.get(0).baseImpactScore();

        // Add diminishing contribution from additional events
        for (int i = 1; i < matchedRules.size(); i++) {
            double contribution = matchedRules.get(i).baseImpactScore() * (1.0 / (i + 1));
            aggregated += contribution;
        }

        int result = (int) Math.min(100, Math.round(aggregated));
        log.debug("Aggregate impact for {} events: {}", events.size(), result);
        return result;
    }
}
