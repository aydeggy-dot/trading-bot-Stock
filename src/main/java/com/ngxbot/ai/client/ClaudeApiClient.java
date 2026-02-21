package com.ngxbot.ai.client;

import com.ngxbot.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ClaudeApiClient {

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public ClaudeApiClient(@Qualifier("claudeWebClient") WebClient webClient,
                           AiProperties aiProperties) {
        this.webClient = webClient;
        this.aiProperties = aiProperties;
    }

    public record AiResponse(String content, int inputTokens, int outputTokens, String model) {
    }

    public Optional<AiResponse> sendMessage(String systemPrompt, String userPrompt, String model) {
        try {
            log.debug("Sending AI request to model={}, systemPrompt length={}, userPrompt length={}",
                    model, systemPrompt.length(), userPrompt.length());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", aiProperties.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        log.warn("Claude API 4xx error: status={}", response.statusCode());
                        return response.createException();
                    })
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(aiProperties.getMaxRetries(), Duration.ofSeconds(1))
                            .maxBackoff(Duration.ofSeconds(4))
                            .filter(this::isRetryableError)
                            .doBeforeRetry(signal -> log.debug("Retrying Claude API call, attempt {}",
                                    signal.totalRetries() + 1)))
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .block();

            if (responseBody == null) {
                log.warn("Claude API returned null response body");
                return Optional.empty();
            }

            return parseResponse(responseBody, model);

        } catch (Exception e) {
            log.warn("Claude API call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<AiResponse> parseResponse(Map<String, Object> responseBody, String requestModel) {
        try {
            List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) responseBody.get("content");
            if (contentBlocks == null || contentBlocks.isEmpty()) {
                log.warn("Claude API response has no content blocks");
                return Optional.empty();
            }

            String textContent = contentBlocks.stream()
                    .filter(block -> "text".equals(block.get("type")))
                    .map(block -> (String) block.get("text"))
                    .findFirst()
                    .orElse(null);

            if (textContent == null) {
                log.warn("Claude API response has no text content block");
                return Optional.empty();
            }

            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            int inputTokens = 0;
            int outputTokens = 0;
            if (usage != null) {
                inputTokens = ((Number) usage.getOrDefault("input_tokens", 0)).intValue();
                outputTokens = ((Number) usage.getOrDefault("output_tokens", 0)).intValue();
            }

            String responseModel = (String) responseBody.getOrDefault("model", requestModel);

            log.debug("Claude API response: model={}, inputTokens={}, outputTokens={}, contentLength={}",
                    responseModel, inputTokens, outputTokens, textContent.length());

            return Optional.of(new AiResponse(textContent, inputTokens, outputTokens, responseModel));

        } catch (Exception e) {
            log.warn("Failed to parse Claude API response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            int statusCode = wcre.getStatusCode().value();
            return statusCode == 429 || statusCode >= 500;
        }
        return false;
    }
}
