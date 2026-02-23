package com.ngxbot.integration;

import com.ngxbot.ai.client.AiCostTracker;
import com.ngxbot.ai.client.ClaudeApiClient;
import com.ngxbot.ai.client.ClaudeApiClient.AiResponse;
import com.ngxbot.config.AiProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 6: Claude AI API
 *
 * Verifies:
 *   - Claude API key is configured and valid
 *   - AI analysis requests return structured responses
 *   - Cost tracker correctly calculates usage
 *   - Budget checking works
 *
 * Prereqs: AI_API_KEY set in .env, AI_ENABLED=true
 * NOTE: This test will consume a small amount of API credits (~$0.01)
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step06_ClaudeAiIT extends IntegrationTestBase {

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private AiCostTracker aiCostTracker;

    @Autowired
    private AiProperties aiProperties;

    @Test
    @Order(1)
    @DisplayName("6.1 AI API key is configured and enabled")
    void aiConfigured() {
        assertThat(aiProperties.isEnabled())
                .as("AI_ENABLED must be true for integration tests")
                .isTrue();
        assertThat(aiProperties.getApiKey())
                .as("AI_API_KEY must be set")
                .isNotBlank();

        printResult("AI Config",
                String.format("Enabled: %s, Model: %s, Budget: $%.2f/day, $%.2f/month",
                        aiProperties.isEnabled(),
                        aiProperties.getDefaultModel(),
                        aiProperties.getBudget().getDailyLimitUsd(),
                        aiProperties.getBudget().getMonthlyLimitUsd()));
    }

    @Test
    @Order(2)
    @DisplayName("6.2 Send a simple news analysis prompt to Claude")
    void sendSimpleAnalysisPrompt() {
        String systemPrompt = "You are a Nigerian stock market analyst. " +
                "Respond in JSON format with fields: sentiment (BULLISH/BEARISH/NEUTRAL), " +
                "confidence (0-100), summary (1 sentence).";

        String userPrompt = "Analyze this headline: 'Zenith Bank reports 35% increase in " +
                "profit before tax for FY 2025, declares N3.50 dividend per share'";

        Optional<AiResponse> response = claudeApiClient.sendMessage(
                systemPrompt, userPrompt, aiProperties.getDefaultModel());

        assertThat(response).isPresent();
        AiResponse aiResponse = response.get();

        printResult("Claude AI Response",
                String.format("Content: %s\nInput tokens: %d, Output tokens: %d, Model: %s",
                        aiResponse.content().substring(0, Math.min(300, aiResponse.content().length())),
                        aiResponse.inputTokens(),
                        aiResponse.outputTokens(),
                        aiResponse.model()));

        assertThat(aiResponse.content()).isNotBlank();
        assertThat(aiResponse.inputTokens()).isPositive();
        assertThat(aiResponse.outputTokens()).isPositive();
    }

    @Test
    @Order(3)
    @DisplayName("6.3 Cost tracker records API usage correctly")
    void costTrackerRecordsUsage() {
        // Record a simulated API call cost
        BigDecimal cost = aiCostTracker.recordCost(
                aiProperties.getDefaultModel(),
                500,  // input tokens
                200,  // output tokens
                "integration-test");

        printResult("Cost Tracker",
                String.format("Recorded cost: $%.6f for 500 input + 200 output tokens", cost));

        assertThat(cost).isPositive();

        // Check daily total
        BigDecimal dailyCost = aiCostTracker.getDailyCost(LocalDate.now());
        assertThat(dailyCost).isGreaterThanOrEqualTo(cost);

        // Check monthly total
        BigDecimal monthlyCost = aiCostTracker.getMonthlyCost(YearMonth.now());
        assertThat(monthlyCost).isGreaterThanOrEqualTo(cost);

        System.out.printf("    Daily total: $%.6f, Monthly total: $%.6f%n",
                dailyCost, monthlyCost);
    }

    @Test
    @Order(4)
    @DisplayName("6.4 Budget check allows calls within limits")
    void budgetCheckAllowsCalls() {
        boolean exceeded = aiCostTracker.isBudgetExceeded();
        boolean canAfford = aiCostTracker.canAffordCall();

        printResult("Budget Check",
                String.format("Budget exceeded: %s, Can afford call: %s", exceeded, canAfford));

        // After just one test call, budget should NOT be exceeded
        assertThat(exceeded).isFalse();
        assertThat(canAfford).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("6.5 AI response contains structured analysis content")
    void aiResponseIsStructured() {
        String systemPrompt = "You are an NGX stock market analyst. " +
                "Respond ONLY with valid JSON. Fields: " +
                "impact_level (HIGH/MEDIUM/LOW), affected_stocks (array), " +
                "market_direction (BULLISH/BEARISH/NEUTRAL), reasoning (string).";

        String userPrompt = "Analyze: 'CBN raises monetary policy rate by 50 basis points " +
                "to 27.75%, citing persistent inflationary pressures.'";

        Optional<AiResponse> response = claudeApiClient.sendMessage(
                systemPrompt, userPrompt, aiProperties.getDefaultModel());

        assertThat(response).isPresent();
        String content = response.get().content();

        printResult("Structured AI Analysis",
                String.format("Response:\n%s", content));

        // Should contain JSON-like structure
        assertThat(content).containsAnyOf("{", "impact_level", "market_direction");
    }
}
