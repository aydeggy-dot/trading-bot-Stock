package com.ngxbot.news;

import com.ngxbot.common.model.EventType;
import com.ngxbot.news.classifier.EventImpactRules;
import com.ngxbot.news.classifier.EventImpactRules.ImpactRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventImpactRulesTest {

    private EventImpactRules rules;

    @BeforeEach
    void setUp() {
        rules = new EventImpactRules();
    }

    @Test
    void tradingSuspensionHasZeroConfidence() {
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.TRADING_SUSPENSION);
        assertThat(rule).isPresent();
        assertThat(rule.get().confidenceModifier()).isEqualTo(0.0);
        assertThat(rule.get().baseImpactScore()).isEqualTo(100);
    }

    @Test
    void delistingHasZeroConfidence() {
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.DELISTING_NOTICE);
        assertThat(rule).isPresent();
        assertThat(rule.get().confidenceModifier()).isEqualTo(0.0);
    }

    @Test
    void cbnPolicyHasHighestNonKillImpact() {
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.CBN_POLICY);
        assertThat(rule).isPresent();
        assertThat(rule.get().baseImpactScore()).isEqualTo(95);
        assertThat(rule.get().confidenceModifier()).isEqualTo(1.4);
    }

    @Test
    void dividendIsPositiveSentiment() {
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.DIVIDEND_ANNOUNCEMENT);
        assertThat(rule).isPresent();
        assertThat(rule.get().defaultSentiment()).isEqualTo("POSITIVE");
    }

    @Test
    void calculateImpactAggregatesMultipleEvents() {
        int impact = rules.calculateImpact(List.of(
                EventType.EARNINGS_RELEASE,    // 80
                EventType.DIVIDEND_ANNOUNCEMENT // 70
        ));
        // 80 + 70 * (1/2) = 80 + 35 = 115 → capped at 100
        assertThat(impact).isGreaterThan(0).isLessThanOrEqualTo(100);
    }

    @Test
    void calculateImpactSingleEvent() {
        int impact = rules.calculateImpact(List.of(EventType.SECTOR_NEWS));
        assertThat(impact).isEqualTo(30);
    }

    @Test
    void calculateImpactEmptyList() {
        assertThat(rules.calculateImpact(List.of())).isEqualTo(0);
    }

    @Test
    void calculateImpactNullList() {
        assertThat(rules.calculateImpact(null)).isEqualTo(0);
    }

    @Test
    void calculateImpactWithSignalKillerReturns100() {
        int impact = rules.calculateImpact(List.of(
                EventType.EARNINGS_RELEASE,
                EventType.TRADING_SUSPENSION
        ));
        assertThat(impact).isEqualTo(100);
    }

    @Test
    void unknownTradingEventReturnsEmpty() {
        // TRADE_SIGNAL is a trading event, not a news event — no rule defined
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.TRADE_SIGNAL);
        assertThat(rule).isEmpty();
    }

    @Test
    void acquisitionMergerIsNeutralSentiment() {
        Optional<ImpactRule> rule = rules.getImpactRule(EventType.ACQUISITION_MERGER);
        assertThat(rule).isPresent();
        assertThat(rule.get().defaultSentiment()).isEqualTo("NEUTRAL");
        assertThat(rule.get().baseImpactScore()).isEqualTo(90);
    }
}
