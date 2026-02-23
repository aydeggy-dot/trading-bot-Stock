package com.ngxbot.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for production integration tests.
 * These tests run against REAL external services (PostgreSQL, EODHD, Telegram, etc.)
 * and require a properly configured .env file + Docker infrastructure.
 *
 * Run with: mvn test -Dspring.profiles.active=integration -Dgroups=integration
 *
 * PREREQUISITES:
 *   1. .env file with real credentials
 *   2. Docker Desktop running
 *   3. docker compose up -d postgres waha
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationTestBase {

    /**
     * Utility: pause for visual verification during headed browser tests.
     */
    protected void pauseForVerification(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Utility: print a step result to console for manual review.
     */
    protected void printResult(String step, String detail) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.printf("  ✓ INTEGRATION TEST: %s%n", step);
        System.out.printf("    %s%n", detail);
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
    }
}
