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
public class AiNewsAnalyzer {

    private static final String NEWS_SYSTEM_PROMPT =
            "You are a Nigerian stock market analyst. Analyze this news article's impact on stock prices. " +
            "Respond in JSON with: sentiment (POSITIVE/NEGATIVE/NEUTRAL), confidence (0-100), " +
            "summary (1 sentence), keyInsights (bullet points as a single string), " +
            "predictedImpact (HIGH/MEDIUM/LOW/NONE), sectorImplications (brief text or null).";

    private static final int MAX_BODY_LENGTH = 2000;
    private static final int LOW_CONFIDENCE_THRESHOLD = 50;

    private final ClaudeApiClient claudeApiClient;
    private final AiCostTracker aiCostTracker;
    private final AiFallbackHandler aiFallbackHandler;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiNewsAnalyzer(ClaudeApiClient claudeApiClient,
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

    public Optional<AiAnalysis> analyzeArticle(NewsItem item) {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping article: {}", item.getTitle());
            return Optional.empty();
        }

        if (!aiCostTracker.canAffordCall()) {
            log.warn("AI budget exceeded, skipping analysis for article: {}", item.getTitle());
            return Optional.empty();
        }

        return aiFallbackHandler.executeWithFallback(
                () -> doAnalyzeArticle(item),
                "analyzeArticle:" + item.getId()
        );
    }

    private Optional<AiAnalysis> doAnalyzeArticle(NewsItem item) {
        String userPrompt = buildUserPrompt(item);

        // First pass: use default model (Haiku)
        String model = aiProperties.getDefaultModel();
        boolean isDeepTrigger = isDeepAnalysisTrigger(item);

        if (isDeepTrigger) {
            log.info("Deep analysis trigger detected for article: {}, using model: {}",
                    item.getTitle(), aiProperties.getDeepAnalysisModel());
            model = aiProperties.getDeepAnalysisModel();
        }

        Optional<AiResponse> response = claudeApiClient.sendMessage(NEWS_SYSTEM_PROMPT, userPrompt, model);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        AiResponse aiResponse = response.get();
        BigDecimal cost = aiCostTracker.recordCost(
                aiResponse.model(), aiResponse.inputTokens(), aiResponse.outputTokens(), "NEWS_ANALYSIS");

        // Parse the JSON response
        Optional<AiAnalysis> analysis = parseNewsResponse(aiResponse, item, cost);
        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        AiAnalysis result = analysis.get();

        // If confidence is low and we used Haiku, escalate to Sonnet
        if (!isDeepTrigger && result.getConfidenceScore() < LOW_CONFIDENCE_THRESHOLD) {
            log.info("Low confidence ({}) from Haiku for article: {}, escalating to Sonnet",
                    result.getConfidenceScore(), item.getTitle());

            if (!aiCostTracker.canAffordCall()) {
                log.warn("Cannot afford Sonnet escalation, using Haiku result");
                AiAnalysis saved = aiAnalysisRepository.save(result);
                return Optional.of(saved);
            }

            String deepModel = aiProperties.getDeepAnalysisModel();
            Optional<AiResponse> deepResponse = claudeApiClient.sendMessage(NEWS_SYSTEM_PROMPT, userPrompt, deepModel);
            if (deepResponse.isPresent()) {
                AiResponse deepAiResponse = deepResponse.get();
                BigDecimal deepCost = aiCostTracker.recordCost(
                        deepAiResponse.model(), deepAiResponse.inputTokens(), deepAiResponse.outputTokens(),
                        "NEWS_ANALYSIS_DEEP");

                Optional<AiAnalysis> deepAnalysis = parseNewsResponse(deepAiResponse, item, deepCost);
                if (deepAnalysis.isPresent()) {
                    result = deepAnalysis.get();
                }
            }
        }

        AiAnalysis saved = aiAnalysisRepository.save(result);
        log.info("AI analysis saved for article '{}': sentiment={}, confidence={}, impact={}",
                item.getTitle(), saved.getSentiment(), saved.getConfidenceScore(), saved.getPredictedImpact());
        return Optional.of(saved);
    }

    private String buildUserPrompt(NewsItem item) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Headline: ").append(item.getTitle()).append("\n");

        if (item.getSymbols() != null && item.getSymbols().length > 0) {
            prompt.append("Related symbols: ").append(String.join(", ", item.getSymbols())).append("\n");
        }

        if (item.getSource() != null) {
            prompt.append("Source: ").append(item.getSource()).append("\n");
        }

        if (item.getSummary() != null) {
            String body = item.getSummary();
            if (body.length() > MAX_BODY_LENGTH) {
                body = body.substring(0, MAX_BODY_LENGTH) + "... [truncated]";
            }
            prompt.append("Body: ").append(body).append("\n");
        }

        return prompt.toString();
    }

    private boolean isDeepAnalysisTrigger(NewsItem item) {
        if (item.getSentiment() == null) {
            return false;
        }
        return aiProperties.getDeepAnalysisTriggers().stream()
                .anyMatch(trigger -> trigger.equalsIgnoreCase(item.getSentiment()));
    }

    private Optional<AiAnalysis> parseNewsResponse(AiResponse aiResponse, NewsItem item, BigDecimal cost) {
        try {
            String content = extractJsonFromResponse(aiResponse.content());
            JsonNode json = objectMapper.readTree(content);

            String symbol = null;
            if (item.getSymbols() != null && item.getSymbols().length > 0) {
                symbol = item.getSymbols()[0];
            }

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
                    .analysisType("NEWS")
                    .inputTokens(aiResponse.inputTokens())
                    .outputTokens(aiResponse.outputTokens())
                    .costUsd(cost)
                    .build();

            return Optional.of(analysis);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI JSON response for article '{}': {}", item.getTitle(), e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonFromResponse(String content) {
        // Handle cases where the model wraps JSON in markdown code blocks
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
