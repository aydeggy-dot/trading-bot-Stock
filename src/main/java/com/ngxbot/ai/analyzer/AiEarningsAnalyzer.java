package com.ngxbot.ai.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngxbot.ai.client.AiCostTracker;
import com.ngxbot.ai.client.AiFallbackHandler;
import com.ngxbot.ai.client.ClaudeApiClient;
import com.ngxbot.ai.client.ClaudeApiClient.AiResponse;
import com.ngxbot.ai.entity.AiAnalysis;
import com.ngxbot.ai.repository.AiAnalysisRepository;
import com.ngxbot.config.AiProperties;
import com.ngxbot.data.entity.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
public class AiEarningsAnalyzer {

    private static final String EARNINGS_SYSTEM_PROMPT =
            "You are a Nigerian stock market analyst specializing in earnings analysis for NGX-listed companies. " +
            "Analyze this earnings report comprehensively. Respond in JSON with: " +
            "sentiment (POSITIVE/NEGATIVE/NEUTRAL), confidence (0-100), " +
            "summary (1-2 sentences on overall earnings quality), " +
            "keyInsights (bullet points as a single string covering revenue, profit, margins), " +
            "predictedImpact (HIGH/MEDIUM/LOW/NONE), " +
            "forwardGuidance (assessment of company's forward-looking statements and outlook), " +
            "managementTone (OPTIMISTIC/CAUTIOUS/DEFENSIVE/NEUTRAL — based on language used), " +
            "revenueQuality (HIGH/MEDIUM/LOW — based on sustainability and source diversification), " +
            "sectorImplications (how this affects the broader sector).";

    private static final int MAX_BODY_LENGTH = 2000;

    private final ClaudeApiClient claudeApiClient;
    private final AiCostTracker aiCostTracker;
    private final AiFallbackHandler aiFallbackHandler;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiEarningsAnalyzer(ClaudeApiClient claudeApiClient,
                              AiCostTracker aiCostTracker,
                              AiFallbackHandler aiFallbackHandler,
                              AiAnalysisRepository aiAnalysisRepository,
                              AiProperties aiProperties,
                              ObjectMapper objectMapper) {
        this.claudeApiClient = claudeApiClient;
        this.aiCostTracker = aiCostTracker;
        this.aiFallbackHandler = aiFallbackHandler;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public Optional<AiAnalysis> analyzeEarnings(NewsItem item, String symbol) {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping earnings analysis for: {}", symbol);
            return Optional.empty();
        }

        if (!aiCostTracker.canAffordCall()) {
            log.warn("AI budget exceeded, skipping earnings analysis for: {}", symbol);
            return Optional.empty();
        }

        return aiFallbackHandler.executeWithFallback(
                () -> doAnalyzeEarnings(item, symbol),
                "analyzeEarnings:" + symbol
        );
    }

    private Optional<AiAnalysis> doAnalyzeEarnings(NewsItem item, String symbol) {
        String userPrompt = buildEarningsPrompt(item, symbol);

        // Earnings always use Sonnet for deeper analysis
        String model = aiProperties.getDeepAnalysisModel();

        Optional<AiResponse> response = claudeApiClient.sendMessage(EARNINGS_SYSTEM_PROMPT, userPrompt, model);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        AiResponse aiResponse = response.get();
        BigDecimal cost = aiCostTracker.recordCost(
                aiResponse.model(), aiResponse.inputTokens(), aiResponse.outputTokens(), "EARNINGS_ANALYSIS");

        Optional<AiAnalysis> analysis = parseEarningsResponse(aiResponse, item, symbol, cost);
        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        AiAnalysis saved = aiAnalysisRepository.save(analysis.get());
        log.info("Earnings analysis saved for {}: sentiment={}, confidence={}, managementTone={}, revenueQuality={}",
                symbol, saved.getSentiment(), saved.getConfidenceScore(),
                saved.getManagementTone(), saved.getRevenueQuality());
        return Optional.of(saved);
    }

    private String buildEarningsPrompt(NewsItem item, String symbol) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Company: ").append(symbol).append("\n");
        prompt.append("Headline: ").append(item.getTitle()).append("\n");

        if (item.getSummary() != null) {
            String body = item.getSummary();
            if (body.length() > MAX_BODY_LENGTH) {
                body = body.substring(0, MAX_BODY_LENGTH) + "... [truncated]";
            }
            prompt.append("Earnings Report Content: ").append(body).append("\n");
        }

        if (item.getSource() != null) {
            prompt.append("Source: ").append(item.getSource()).append("\n");
        }

        return prompt.toString();
    }

    private Optional<AiAnalysis> parseEarningsResponse(AiResponse aiResponse, NewsItem item,
                                                        String symbol, BigDecimal cost) {
        try {
            String content = extractJsonFromResponse(aiResponse.content());
            JsonNode json = objectMapper.readTree(content);

            AiAnalysis analysis = AiAnalysis.builder()
                    .newsItemId(item.getId())
                    .symbol(symbol)
                    .model(aiResponse.model())
                    .sentiment(getJsonText(json, "sentiment", "NEUTRAL"))
                    .confidenceScore(getJsonInt(json, "confidence", 0))
                    .summary(getJsonText(json, "summary", null))
                    .keyInsights(getJsonText(json, "keyInsights", null))
                    .predictedImpact(getJsonText(json, "predictedImpact", "NONE"))
                    .sectorImplications(getJsonText(json, "sectorImplications", null))
                    .forwardGuidance(getJsonText(json, "forwardGuidance", null))
                    .managementTone(getJsonText(json, "managementTone", null))
                    .revenueQuality(getJsonText(json, "revenueQuality", null))
                    .analysisType("EARNINGS")
                    .inputTokens(aiResponse.inputTokens())
                    .outputTokens(aiResponse.outputTokens())
                    .costUsd(cost)
                    .build();

            return Optional.of(analysis);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse earnings AI response for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonFromResponse(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return content.trim();
    }

    private String getJsonText(JsonNode json, String field, String defaultValue) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asText(defaultValue);
    }

    private int getJsonInt(JsonNode json, String field, int defaultValue) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asInt(defaultValue);
    }
}
