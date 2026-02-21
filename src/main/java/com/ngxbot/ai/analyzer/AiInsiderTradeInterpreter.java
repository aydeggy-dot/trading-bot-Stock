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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
public class AiInsiderTradeInterpreter {

    private static final String INSIDER_SYSTEM_PROMPT =
            "You are a Nigerian stock market analyst specializing in insider trading patterns on the NGX. " +
            "Analyze the following insider trade activity and assess its implications. Respond in JSON with: " +
            "sentiment (POSITIVE/NEGATIVE/NEUTRAL — based on whether insiders are buying or selling), " +
            "confidence (0-100), " +
            "summary (1-2 sentences on what the insider activity signals), " +
            "keyInsights (bullet points as a single string covering trade size, frequency, insider role, pattern), " +
            "predictedImpact (HIGH/MEDIUM/LOW/NONE), " +
            "sectorImplications (whether this signals broader sector trends).";

    private final ClaudeApiClient claudeApiClient;
    private final AiCostTracker aiCostTracker;
    private final AiFallbackHandler aiFallbackHandler;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiInsiderTradeInterpreter(ClaudeApiClient claudeApiClient,
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

    public Optional<AiAnalysis> interpretInsiderTrade(String symbol, String insiderTradeDetails) {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping insider trade interpretation for: {}", symbol);
            return Optional.empty();
        }

        if (!aiCostTracker.canAffordCall()) {
            log.warn("AI budget exceeded, skipping insider trade interpretation for: {}", symbol);
            return Optional.empty();
        }

        return aiFallbackHandler.executeWithFallback(
                () -> doInterpretInsiderTrade(symbol, insiderTradeDetails),
                "interpretInsiderTrade:" + symbol
        );
    }

    private Optional<AiAnalysis> doInterpretInsiderTrade(String symbol, String insiderTradeDetails) {
        String userPrompt = buildInsiderPrompt(symbol, insiderTradeDetails);

        // Insider trades use Haiku
        String model = aiProperties.getDefaultModel();

        Optional<AiResponse> response = claudeApiClient.sendMessage(INSIDER_SYSTEM_PROMPT, userPrompt, model);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        AiResponse aiResponse = response.get();
        BigDecimal cost = aiCostTracker.recordCost(
                aiResponse.model(), aiResponse.inputTokens(), aiResponse.outputTokens(),
                "INSIDER_TRADE_INTERPRETATION");

        Optional<AiAnalysis> analysis = parseInsiderResponse(aiResponse, symbol, cost);
        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        AiAnalysis saved = aiAnalysisRepository.save(analysis.get());
        log.info("Insider trade analysis saved for {}: sentiment={}, confidence={}, impact={}",
                symbol, saved.getSentiment(), saved.getConfidenceScore(), saved.getPredictedImpact());
        return Optional.of(saved);
    }

    private String buildInsiderPrompt(String symbol, String insiderTradeDetails) {
        return "Symbol: " + symbol + "\n" +
                "Insider Trade Details:\n" + insiderTradeDetails;
    }

    private Optional<AiAnalysis> parseInsiderResponse(AiResponse aiResponse, String symbol, BigDecimal cost) {
        try {
            String content = extractJsonFromResponse(aiResponse.content());
            JsonNode json = objectMapper.readTree(content);

            AiAnalysis analysis = AiAnalysis.builder()
                    .symbol(symbol)
                    .model(aiResponse.model())
                    .sentiment(getJsonText(json, "sentiment", "NEUTRAL"))
                    .confidenceScore(getJsonInt(json, "confidence", 0))
                    .summary(getJsonText(json, "summary", null))
                    .keyInsights(getJsonText(json, "keyInsights", null))
                    .predictedImpact(getJsonText(json, "predictedImpact", "NONE"))
                    .sectorImplications(getJsonText(json, "sectorImplications", null))
                    .analysisType("INSIDER")
                    .inputTokens(aiResponse.inputTokens())
                    .outputTokens(aiResponse.outputTokens())
                    .costUsd(cost)
                    .build();

            return Optional.of(analysis);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse insider trade AI response for {}: {}", symbol, e.getMessage());
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
