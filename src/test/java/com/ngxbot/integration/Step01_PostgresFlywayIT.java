package com.ngxbot.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1: Infrastructure — PostgreSQL + Flyway
 *
 * Verifies:
 *   - PostgreSQL connection is alive
 *   - All 30 Flyway migrations executed successfully
 *   - All expected tables exist with correct schema
 *
 * Prereqs: docker compose up -d postgres
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Step01_PostgresFlywayIT extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // All tables expected after V1–V30 migrations
    private static final List<String> EXPECTED_TABLES = List.of(
            "ohlcv_bars",              // V1
            "etf_valuations",          // V2
            "trade_orders",            // V3
            "positions",               // V4
            "portfolio_snapshots",     // V5
            "corporate_actions",       // V6
            "market_indices",          // V7
            "watchlist_stocks",        // V8
            "trade_signals",           // V9
            "notification_log",        // V10
            "kill_switch_state",       // V11
            "approval_requests",       // V12
            "news_items",              // V13
            "broker_sessions",         // V14
            "risk_events",             // V15
            "circuit_breaker_log",     // V16
            "sector_mapping",          // V17
            "system_config",           // V18
            "ai_analysis",             // V21
            "ai_cost_ledger",          // V21
            "discovered_stocks",       // V23
            "discovery_events",        // V24
            "core_holdings",           // V25
            "dca_plans",               // V26
            "dividend_events",         // V27
            "rebalance_actions",       // V28
            "backtest_runs",           // V30
            "backtest_trades",         // V30
            "equity_curve_points"      // V30
    );

    @Test
    @Order(1)
    @DisplayName("1.1 PostgreSQL connection is alive")
    void postgresConnectionIsAlive() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(5)).isTrue();

            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            String dbVersion = meta.getDatabaseProductVersion();

            printResult("PostgreSQL Connection",
                    String.format("%s %s — connected successfully", dbProduct, dbVersion));

            assertThat(dbProduct).isEqualToIgnoringCase("PostgreSQL");
        }
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Flyway ran all 30 migrations successfully")
    void flywayRanAllMigrations() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        printResult("Flyway Migrations",
                String.format("%d successful migrations found", migrationCount));

        assertThat(migrationCount).isGreaterThanOrEqualTo(30);
    }

    @Test
    @Order(3)
    @DisplayName("1.3 All expected tables exist in the database")
    void allExpectedTablesExist() throws Exception {
        List<String> actualTables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    actualTables.add(rs.getString("TABLE_NAME"));
                }
            }
        }

        List<String> missingTables = EXPECTED_TABLES.stream()
                .filter(t -> !actualTables.contains(t))
                .toList();

        printResult("Table Verification",
                String.format("%d tables found, %d expected, %d missing: %s",
                        actualTables.size(), EXPECTED_TABLES.size(),
                        missingTables.size(), missingTables));

        assertThat(missingTables)
                .as("Missing tables: %s", missingTables)
                .isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("1.4 ohlcv_bars table has correct column structure")
    void ohlcvBarsTableStructure() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'ohlcv_bars' ORDER BY ordinal_position",
                String.class);

        printResult("ohlcv_bars Schema", String.format("Columns: %s", columns));

        assertThat(columns).contains("id", "symbol", "trade_date",
                "open_price", "high_price", "low_price", "close_price", "volume");
    }

    @Test
    @Order(5)
    @DisplayName("1.5 trade_orders table has correct column structure")
    void tradeOrdersTableStructure() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'trade_orders' ORDER BY ordinal_position",
                String.class);

        printResult("trade_orders Schema", String.format("Columns: %s", columns));

        assertThat(columns).contains("id", "order_id", "symbol", "side",
                "quantity", "intended_price", "status", "strategy");
    }

    @Test
    @Order(6)
    @DisplayName("1.6 Flyway schema history shows no failed migrations")
    void noFailedMigrations() {
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                Integer.class);

        printResult("Migration Health", String.format("%d failed migrations", failedCount));

        assertThat(failedCount).isZero();
    }

    @Test
    @Order(7)
    @DisplayName("1.7 Initial seed data exists (watchlist stocks)")
    void seedDataExists() {
        Integer watchlistCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_stocks", Integer.class);

        printResult("Seed Data", String.format("%d watchlist stocks seeded", watchlistCount));

        assertThat(watchlistCount).isGreaterThan(0);
    }
}
