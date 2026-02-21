package com.ngxbot.news;

import com.ngxbot.common.model.EventType;
import com.ngxbot.news.classifier.NewsEventClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsEventClassifierTest {

    private NewsEventClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new NewsEventClassifier();
    }

    @Test
    void classifyDividendHeadline() {
        List<EventType> result = classifier.classifyHeadline("Zenith Bank declares N3.50 dividend per share");
        assertThat(result).contains(EventType.DIVIDEND_ANNOUNCEMENT);
    }

    @Test
    void classifyEarningsHeadline() {
        List<EventType> result = classifier.classifyHeadline("GTCO reports record quarterly profit of N250 billion");
        assertThat(result).contains(EventType.EARNINGS_RELEASE);
    }

    @Test
    void classifyAcquisitionHeadline() {
        List<EventType> result = classifier.classifyHeadline("Access Corp completes acquisition of new subsidiary bank");
        assertThat(result).contains(EventType.ACQUISITION_MERGER);
    }

    @Test
    void classifyCbnPolicy() {
        List<EventType> result = classifier.classifyHeadline("CBN raises MPR to 27.5% in surprise monetary policy decision");
        assertThat(result).contains(EventType.CBN_POLICY);
    }

    @Test
    void classifyInsiderTrade() {
        List<EventType> result = classifier.classifyHeadline("Director of Dangcem bought 500,000 shares via Form 29 filing");
        assertThat(result).contains(EventType.INSIDER_TRADE);
    }

    @Test
    void classifyManagementChange() {
        List<EventType> result = classifier.classifyHeadline("New CEO appointed at FBNH after board reshuffle");
        assertThat(result).contains(EventType.MANAGEMENT_CHANGE);
    }

    @Test
    void classifySuspension() {
        List<EventType> result = classifier.classifyHeadline("SEC suspends trading in ABC stock pending investigation");
        assertThat(result).contains(EventType.TRADING_SUSPENSION);
    }

    @Test
    void classifyBonusIssue() {
        List<EventType> result = classifier.classifyHeadline("Dangcem announces 1:10 bonus issue to shareholders");
        assertThat(result).contains(EventType.BONUS_ISSUE);
    }

    @Test
    void classifyShareBuyback() {
        List<EventType> result = classifier.classifyHeadline("MTNN launches N50 billion share buyback programme");
        assertThat(result).contains(EventType.SHARE_BUYBACK);
    }

    @Test
    void classifyMultipleEvents() {
        List<EventType> result = classifier.classifyHeadline(
                "GTCO reports record earnings and declares interim dividend of N2.50");
        assertThat(result).contains(EventType.EARNINGS_RELEASE, EventType.DIVIDEND_ANNOUNCEMENT);
    }

    @Test
    void classifyIrrelevantHeadline() {
        List<EventType> result = classifier.classifyHeadline("Weather forecast for Lagos shows sunny skies this week");
        assertThat(result).isEmpty();
    }

    @Test
    void classifyNullHeadlineReturnsEmpty() {
        assertThat(classifier.classifyHeadline(null)).isEmpty();
    }

    @Test
    void classifyBlankHeadlineReturnsEmpty() {
        assertThat(classifier.classifyHeadline("  ")).isEmpty();
    }

    @Test
    void classifyDelistingNotice() {
        List<EventType> result = classifier.classifyHeadline("XYZ Corporation announces voluntary delisting from NGX");
        assertThat(result).contains(EventType.DELISTING_NOTICE);
    }

    @Test
    void classifyRightsIssue() {
        List<EventType> result = classifier.classifyHeadline("Zenith Bank proposes N200 billion rights issue");
        assertThat(result).contains(EventType.RIGHTS_ISSUE);
    }
}
