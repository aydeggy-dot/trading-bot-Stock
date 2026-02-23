package com.ngxbot.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

/**
 * Step 10: Dashboard REST API Verification
 *
 * Verifies every dashboard endpoint returns 200 OK with valid JSON.
 * Uses Spring's TestRestTemplate with the real running app.
 *
 * Prereqs: Full application running (Steps 1-6 passing)
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step10_DashboardIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ---- Portfolio Endpoints ----

    @Test
    @Order(1)
    @DisplayName("10.1 GET /api/portfolio — portfolio overview")
    void getPortfolio() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/portfolio", String.class);

        printResult("GET /api/portfolio",
                String.format("Status: %s, Body length: %d",
                        response.getStatusCode(),
                        response.getBody() != null ? response.getBody().length() : 0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("10.2 GET /api/fx — FX rate information")
    void getFxInfo() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/fx", String.class);

        printResult("GET /api/fx",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(3)
    @DisplayName("10.3 GET /api/settlement — settlement status")
    void getSettlement() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/settlement", String.class);

        printResult("GET /api/settlement",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Performance Endpoint ----

    @Test
    @Order(4)
    @DisplayName("10.4 GET /api/performance — performance metrics")
    void getPerformance() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/performance", String.class);

        printResult("GET /api/performance",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Signal Endpoints ----

    @Test
    @Order(5)
    @DisplayName("10.5 GET /api/signals — today's trade signals")
    void getTodaySignals() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/signals", String.class);

        printResult("GET /api/signals",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(6)
    @DisplayName("10.6 GET /api/news — recent news items")
    void getRecentNews() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/news", String.class);

        printResult("GET /api/news",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(7)
    @DisplayName("10.7 GET /api/ai/cost — AI cost summary")
    void getAiCostSummary() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/ai/cost", String.class);

        printResult("GET /api/ai/cost",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Discovery Endpoints ----

    @Test
    @Order(8)
    @DisplayName("10.8 GET /api/discovery/active — active discovered stocks")
    void getActiveDiscoveries() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/discovery/active", String.class);

        printResult("GET /api/discovery/active",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(9)
    @DisplayName("10.9 GET /api/discovery/candidates — candidate stocks")
    void getCandidateStocks() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/discovery/candidates", String.class);

        printResult("GET /api/discovery/candidates",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Backtest Endpoints ----

    @Test
    @Order(10)
    @DisplayName("10.10 GET /api/backtest/runs — backtest run history")
    void getBacktestRuns() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/backtest/runs", String.class);

        printResult("GET /api/backtest/runs",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(11)
    @DisplayName("10.11 GET /api/backtest/strategies — available strategies")
    void getBacktestStrategies() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/backtest/strategies", String.class);

        printResult("GET /api/backtest/strategies",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Actuator ----

    @Test
    @Order(12)
    @DisplayName("10.12 GET /actuator/health — application health check")
    void actuatorHealth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);

        printResult("GET /actuator/health",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    // ---- Kill Switch ----

    @Test
    @Order(13)
    @DisplayName("10.13 GET /api/killswitch — kill switch status")
    void getKillSwitchStatus() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/killswitch", String.class);

        printResult("GET /api/killswitch",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- Dividends ----

    @Test
    @Order(14)
    @DisplayName("10.14 GET /api/dividends — dividend events")
    void getDividends() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/dividends", String.class);

        printResult("GET /api/dividends",
                String.format("Status: %s, Body: %s",
                        response.getStatusCode(),
                        truncateBody(response.getBody())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String truncateBody(String body) {
        if (body == null) return "null";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
