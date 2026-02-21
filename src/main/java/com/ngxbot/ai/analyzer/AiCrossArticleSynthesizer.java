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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiCrossArticleSynthesizer {

    private static final String CROSS_ARTICLE_SYSTEM_PROMPT =
            "You are a Nigerian stock market analyst. You are given multiple news articles about the same stock. " +
            "Synthesize a consensus view across all articles. Respond in JSON with: " +
            "sentiment (POSITIVE/NEGATIVE/NEUTRAL — the consensus), confidence (0-100), " +
            "summary (1-2 sentences on the overall narrative), " +
            "keyInsights (bullet points as a single string highlighting the emerging narrative and any contradictions), " +
            "predictedImpact (HIGH/MEDIUM/LOW/NONE), " +
            "sectorImplications (broader sector impact if any).";

    private final ClaudeApiClient claudeApiClient;
    private final AiCostTracker aiCostTracker;
    private final AiFallbackHandler aiFallbackHandler;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiCrossArticleSynthesizer(ClaudeApiClient claudeApiClient,
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

    public Optional<AiAnalysis> synthesize(String symbol, List<NewsItem> articles) {
        if (!aiProperties.isEnabled()) {
            log.debug("AI analysis disabled, skipping cross-article synthesis for: {}", symbol);
            return Optional.empty();
        }

        if (articles == null || articles.size() < 2) {
            log.debug("Need at least 2 articles for cross-article synthesis, got {} for symbol: {}",
                    articles == null ? 0 : articles.size(), symbol);
            return Optional.empty();
        }

        if (!aiCostTracker.canAffordCall()) {
            log.warn("AI budget exceeded, skipping cross-article synthesis for: {}", symbol);
            return Optional.empty();
        }

        return aiFallbackHandler.executeWithFallback(
                () -> doSynthesize(symbol, articles),
                "crossArticleSynthesis:" + symbol
        );
    }

    private Optional<AiAnalysis> doSynthesize(String symbol, List<NewsItem> articles) {
        String userPrompt = buildSynthesisPrompt(symbol, articles);

        // Cross-article synthesis uses Haiku
        String model = aiProperties.getDefaultModel();

        Optional<AiResponse> response = claudeApiClient.sendMessage(CROSS_ARTICLE_SYSTEM_PROMPT, userPrompt, model);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        AiResponse aiResponse = response.get();
        BigDecimal cost = aiCostTracker.recordCost(
                aiResponse.model(), aiResponse.inputTokens(), aiResponse.outputTokens(),
                "CROSS_ARTICLE_SYNTHESIS");

        Optional<AiAnalysis> analysis = parseSynthesisResponse(aiResponse, symbol, cost);
        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        AiAnalysis saved = aiAnalysisRepository.save(analysis.get());
        log.info("Cross-article synthesis saved for {}: sentiment={}, confidence={}, articles={}",
                symbol, saved.getSentiment(), saved.getConfidenceScore(), articles.size());
        return Optional.of(saved);
    }

    private String buildSynthesisPrompt(String symbol, List<NewsItem> articles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Symbol: ").append(symbol).append("\n");
        prompt.append("Number of articles: ").append(articles.size()).append("\n\n");

        String articlesText = articles.stream()
                .map(article -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("- Title: ").append(article.getTitle());
                    if (article.getSummary() != null) {
                        String summary = article.getSummary();
                        if (summary.length() > 200) {
                            summary = summary.substring(0, 200) + "...";
                        }
                        sb.append("\n  Summary: ").append(summary);
                    }
                    if (article.getSource() != null) {
                        sb.append("\n  Source: ").append(article.getSource());
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n\n"));

        prompt.append("Articles:\n").append(articlesText);
        return prompt.toString();
    }

    private Optional<AiAnalysis> parseSynthesisResponse(AiResponse aiResponse, String symbol, BigDecimal cost) {
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
                    .analysisType("CROSS_ARTICLE")
                    .inputTokens(aiResponse.inputTokens())
                    .outputTokens(aiResponse.outputTokens())
                    .costUsd(cost)
                    .build();

            return Optional.of(analysis);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cross-article synthesis response for {}: {}", symbol, e.getMessage());
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
