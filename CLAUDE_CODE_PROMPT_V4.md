# CLAUDE CODE PROMPT — Multi-Market AI Trading Bot (Spring Boot) — V4

> **How to use:** Save as `CLAUDE.md` in project root (Claude Code auto-reads it), or paste as initial prompt. Then build incrementally: "Build Phase 1: Data Pipeline".

---

## PROJECT IDENTITY

**Project:** Multi-Market AI Trading Bot
**Stack:** Java 21 + Spring Boot 3.3+ + Maven + PostgreSQL + Playwright-Java + Anthropic Claude API + Docker
**Broker:** Trove (trovefinance.com) — single broker for BOTH NGX and US (NYSE/NASDAQ) stocks
**Purpose:** Autonomous portfolio management across Nigerian and US stock markets via Trove brokerage, with news intelligence, WhatsApp + Telegram notifications, Core-Satellite portfolio structure covering short-term active trading AND long-term wealth building.

### Why Single Broker (Trove)
- Trove is SEC-Nigeria licensed via fully owned subsidiary Innova Securities Limited
- US trades via DriveWealth LLC (FINRA/SIPC insured up to $500,000)
- Covers both NGX and US stocks on ONE platform — one login, one execution engine
- **Has a real web trading portal:** app.trovefinance.com (JavaScript SPA — Playwright viable)
- Also has an API program (trovefinance.com/api) — future migration option when ready
- For now: Playwright-Java browser automation against Trove's web portal
- Future: swap Playwright calls with REST API calls when API access is pursued (architecture designed for this swap)
- 10,000+ securities, fractional shares, commission-free US trading

---

## DEVELOPER CONTEXT

I am a Spring Boot developer. Build everything using Spring Boot idioms: `@Service`, `@Repository`, `@Scheduled`, `@ConfigurationProperties`, `JpaRepository`, `WebClient`, `application.yml`. Do NOT suggest Python, Node.js, or non-Java solutions. Use Lombok (`@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`) throughout. Use Java 21 features (records, sealed interfaces, pattern matching, virtual threads where appropriate).

---

## PORTFOLIO PHILOSOPHY: CORE-SATELLITE MODEL (MULTI-MARKET)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    TOTAL PORTFOLIO (100%)                               │
│                                                                         │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────┐ │
│  │        CORE (60-70%)         │  │       SATELLITE (30-40%)         │ │
│  │                              │  │                                  │ │
│  │  Long-term wealth building   │  │  Active trading for alpha        │ │
│  │  Hold: months to years       │  │  Hold: days to weeks             │ │
│  │                              │  │                                  │ │
│  │  NGX:                        │  │  NGX:                            │ │
│  │  * Bank dividend stocks      │  │  * ETF NAV arbitrage             │ │
│  │  * Blue-chip value buys      │  │  * Momentum breakout             │ │
│  │                              │  │                                  │ │
│  │  US:                         │  │  US:                             │ │
│  │  * S&P 500 ETF (VOO)        │  │  * Earnings momentum             │ │
│  │  * Dividend ETF (SCHD)      │  │  * US sector rotation            │ │
│  │  * Bond ETF (BND)           │  │                                  │ │
│  │  * Gold ETF (GLD/NEWGOLD)   │  │  BOTH:                           │ │
│  │                              │  │  * News-driven signals           │ │
│  │  DCA accumulation            │  │  * 2% risk per trade             │ │
│  │  Quarterly rebalancing       │  │  * Tight stop-losses (8-10%)     │ │
│  │  Dividend reinvestment       │  │  * Circuit breakers active       │ │
│  │  Wide stop-losses (20-25%)   │  │                                  │ │
│  │  NO circuit breakers         │  │                                  │ │
│  └──────────────────────────────┘  └──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

**CRITICAL RULES:**
- Core and satellite positions tracked SEPARATELY. A stock can appear in BOTH pools.
- NGX and US positions tracked with their native currency (NGN or USD).
- ALL portfolio values roll up to NGN as base currency for unified reporting.
- Risk rules differ per pool AND per market.

---

## ARCHITECTURE OVERVIEW

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  DATA        │────>│  SIGNAL      │────>│  RISK        │────>│  EXECUTION   │
│  PIPELINE    │     │  ENGINE      │     │  MANAGER     │     │  (Playwright │
│              │     │              │     │              │     │   -> Trove)  │
│ * EODHD API  │     │ SHORT-TERM:  │     │ * Pool rules │     │              │
│   (NGX + US) │     │ * RSI, MACD  │     │ * Market     │     │ * Browser    │
│ * ETF NAV    │     │ * NAV arb    │     │   rules      │     │   Session    │
│ * Fundament. │     │ * Momentum   │     │ * FX-aware   │     │   Lock       │
│ * FX rates   │     │ * Earnings   │     │ * Position   │     │              │
│              │     │              │     │   sizing     │     │ * OTP/2FA    │
│ * NEWS       │     │ LONG-TERM:   │     │ * Circuit    │     │   Handler    │
│ * Nairametr. │     │ * Value score│     │   breakers   │     │              │
│ * BusinessDay│     │ * Div yield  │     │ * SETTLE-    │     │ * Order      │
│ * NGX Bullet.│     │ * DCA sched  │     │   MENT CASH  │     │   Recovery   │
│ * Reuters    │     │ * Rebalance  │     │   tracking   │     │              │
│ * Seeking a  │     │              │     └──────────────┘     │ NGX: Naira   │
│ * Earnings   │     │ * NEWS:      │                           │ US: Dollar   │
│   calendar   │     │ * Event type │     ┌──────────────┐     │ Single login │
└──────────────┘     │ * Impact     │     │  LONG-TERM   │     └──────┬───────┘
                     │   scoring    │     │  ENGINE      │            │
                     └──────────────┘     │ * DCA exec   │     ┌──────v───────┐
                                          │ * Rebalancer │     │  WHATSAPP +  │
* RECONCILIATION <──────────────────────> │ * Div tracker│     │  TELEGRAM    │
  on startup + daily                      └──────────────┘     └──────────────┘
  syncs DB with Trove
                     ┌──────────────┐
                     │ * BACKTEST   │
                     │   ENGINE     │
                     │ * Replay     │
                     │ * Simulate   │
                     │ * Report     │
                     └──────────────┘
```

All modules are Spring `@Service` beans wired via constructor injection. No field injection.

---

## PROJECT STRUCTURE

```
trading-bot/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── CLAUDE.md
├── src/main/java/com/tradingbot/
│   ├── TradingBotApplication.java
│   │
│   ├── config/
│   │   ├── AppConfig.java
│   │   ├── PlaywrightConfig.java
│   │   ├── SchedulingConfig.java
│   │   ├── WebClientConfig.java
│   │   ├── RiskProperties.java
│   │   ├── TradingProperties.java
│   │   ├── TroveProperties.java
│   │   ├── NotificationProperties.java
│   │   ├── LongTermProperties.java
│   │   ├── CorePortfolioProperties.java
│   │   ├── NewsProperties.java
│   │   ├── AiProperties.java                  // ★ NEW — Claude API config + cost controls
│   │   ├── DiscoveryProperties.java               // ★ NEW — Stock discovery config
│   │   ├── ReconciliationProperties.java      // ★ NEW
│   │   └── BacktestProperties.java            // ★ NEW
│   │
│   ├── data/
│   │   ├── entity/
│   │   │   ├── OhlcvBar.java              // Has market field (NGX, US)
│   │   │   ├── EtfValuation.java
│   │   │   ├── CorporateAction.java
│   │   │   ├── MarketIndex.java           // ASI, NGX30, S&P500, NASDAQ
│   │   │   ├── WatchlistStock.java        // Has market field
│   │   │   ├── FundamentalData.java       // Has currency field
│   │   │   └── FxRate.java                // ★ NEW — USD/NGN daily rates
│   │   ├── repository/
│   │   │   ├── OhlcvRepository.java
│   │   │   ├── EtfValuationRepository.java
│   │   │   ├── CorporateActionRepository.java
│   │   │   ├── MarketIndexRepository.java
│   │   │   ├── FundamentalDataRepository.java
│   │   │   └── FxRateRepository.java      // ★ NEW
│   │   ├── client/
│   │   │   ├── EodhdApiClient.java        // Handles BOTH .XNSA and .US tickers
│   │   │   ├── NgxWebScraper.java
│   │   │   ├── EtfNavScraper.java
│   │   │   ├── FxRateClient.java          // ★ NEW — USD/NGN from EODHD or CBN
│   │   │   └── UsEarningsCalendarClient.java // ★ NEW
│   │   └── scheduler/
│   │       └── DataCollectionScheduler.java
│   │
│   ├── news/                               // ★★ NEWS INTELLIGENCE MODULE
│   │   ├── entity/
│   │   │   ├── NewsArticle.java
│   │   │   └── NewsEvent.java
│   │   ├── repository/
│   │   │   ├── NewsArticleRepository.java
│   │   │   └── NewsEventRepository.java
│   │   ├── scraper/
│   │   │   ├── NairametricsScraper.java   // NGX news
│   │   │   ├── BusinessDayScraper.java    // NGX macro
│   │   │   ├── NgxBulletinParser.java     // Official NGX daily bulletin PDF
│   │   │   ├── ReutersRssScraper.java     // US/global news
│   │   │   ├── SeekingAlphaScraper.java   // US stock analysis
│   │   │   └── CbnPressScraper.java       // CBN rate decisions, FX policy
│   │   ├── classifier/
│   │   │   ├── NewsEventClassifier.java   // Rule-based event type detection
│   │   │   ├── EventType.java             // Enum: 17 event types
│   │   │   └── EventImpactRules.java      // Maps event types to signal modifiers
│   │   ├── NewsImpactScorer.java
│   │   ├── InsiderTradeDetector.java      // Parses NGX bulletin Form 29 disclosures
│   │   └── scheduler/
│   │       ├── NewsScraperScheduler.java
│   │       └── NgxBulletinScheduler.java
│   │
│   ├── ai/                                // ★★ AI INTELLIGENCE LAYER (Claude API)
│   │   ├── config/
│   │   │   └── AiProperties.java          // API key, model selection, cost controls
│   │   ├── client/
│   │   │   └── ClaudeApiClient.java       // WebClient wrapper for Anthropic Messages API
│   │   ├── AiNewsAnalyzer.java            // Deep article analysis — sentiment, nuance, context
│   │   ├── AiEarningsAnalyzer.java        // Earnings report/transcript interpretation
│   │   ├── AiCrossArticleSynthesizer.java // Aggregates multiple articles per stock per day
│   │   ├── AiInsiderTradeInterpreter.java // Context analysis of insider trade patterns
│   │   ├── AiAnalysisCache.java           // Dedup: don't re-analyze same article
│   │   ├── AiFallbackHandler.java         // Graceful degradation when API unavailable
│   │   ├── AiCostTracker.java             // Tracks daily/monthly token spend vs budget
│   │   ├── entity/
│   │   │   └── AiAnalysis.java            // Persisted AI verdicts for audit trail
│   │   ├── repository/
│   │   │   └── AiAnalysisRepository.java
│   │   └── scheduler/
│   │       └── AiAnalysisScheduler.java   // Batch-processes new articles through AI
│   │
│   ├── discovery/                          // ★★ DYNAMIC STOCK DISCOVERY MODULE
│   │   ├── config/
│   │   │   └── DiscoveryProperties.java   // Scan schedules, filters, limits
│   │   ├── client/
│   │   │   └── EodhdScreenerClient.java   // EODHD Screener API wrapper
│   │   ├── DiscoveryEngine.java           // Orchestrates scan → filter → observe → promote
│   │   ├── WatchlistManager.java          // Dynamic watchlist: SEED + CANDIDATE + PROMOTED
│   │   ├── CandidateEvaluator.java        // Multi-stage scoring of discovered stocks
│   │   ├── NewsDiscoveryListener.java     // Catches news mentions of stocks NOT in watchlist
│   │   ├── PromotionPolicy.java           // Rules for CANDIDATE → PROMOTED transition
│   │   ├── DemotionPolicy.java            // Rules for PROMOTED → DEMOTED transition
│   │   ├── entity/
│   │   │   ├── DiscoveredStock.java       // Status: CANDIDATE, OBSERVATION, PROMOTED, DEMOTED
│   │   │   └── DiscoveryEvent.java        // Audit trail: why added, promoted, or demoted
│   │   ├── repository/
│   │   │   ├── DiscoveredStockRepository.java
│   │   │   └── DiscoveryEventRepository.java
│   │   └── scheduler/
│   │       └── DiscoveryScheduler.java    // Weekly scans + daily observation checks
│   │
│   ├── signal/
│   │   ├── technical/
│   │   │   ├── TechnicalIndicatorService.java
│   │   │   ├── RsiCalculator.java
│   │   │   ├── MacdCalculator.java
│   │   │   ├── MovingAverageCalculator.java
│   │   │   ├── VolumeAnalyzer.java
│   │   │   └── AtrCalculator.java
│   │   ├── fundamental/
│   │   │   ├── NavDiscountCalculator.java
│   │   │   ├── DividendProximityScanner.java
│   │   │   ├── PencomEligibilityChecker.java
│   │   │   ├── FundamentalScorer.java
│   │   │   └── ValueScreener.java
│   │   ├── model/
│   │   │   ├── TradeSignal.java           // Has market, currency fields
│   │   │   ├── SignalStrength.java
│   │   │   ├── IndicatorSnapshot.java
│   │   │   └── PortfolioPool.java
│   │   ├── CompositeSignalScorer.java     // Includes news impact weight
│   │   └── SignalGenerationScheduler.java // Market-aware: different times per market
│   │
│   ├── strategy/
│   │   ├── Strategy.java                  // Interface: getPool(), getMarket()
│   │   ├── EtfNavArbitrageStrategy.java   // SATELLITE, NGX
│   │   ├── MomentumBreakoutStrategy.java  // SATELLITE, BOTH markets
│   │   ├── PensionFlowOverlay.java        // BOTH pools, NGX
│   │   ├── DividendAccumulationStrategy.java // CORE, BOTH markets
│   │   ├── ValueAccumulationStrategy.java   // CORE, BOTH markets
│   │   ├── DcaStrategy.java                 // CORE, BOTH markets
│   │   ├── CurrencyHedgeStrategy.java       // CORE, BOTH (NEWGOLD on NGX, GLD on US)
│   │   ├── SectorRotationStrategy.java      // CORE, BOTH markets
│   │   ├── UsEarningsMomentumStrategy.java  // ★ SATELLITE, US only
│   │   └── UsEtfRotationStrategy.java       // ★ SATELLITE, US only
│   │
│   ├── longterm/
│   │   ├── CorePortfolioManager.java
│   │   ├── DcaExecutor.java               // Multi-currency DCA
│   │   ├── DividendTracker.java           // US dividends: 30% withholding tax awareness
│   │   ├── DividendReinvestmentService.java
│   │   ├── PortfolioRebalancer.java       // Cross-market rebalancing
│   │   ├── FundamentalScreener.java
│   │   ├── TargetAllocationService.java
│   │   ├── model/
│   │   │   ├── CoreHolding.java           // Has market, currency fields
│   │   │   ├── DcaPlan.java               // Has currency, budget in local currency
│   │   │   ├── DividendEvent.java         // Has withholding_tax_pct, net_amount
│   │   │   ├── RebalanceAction.java
│   │   │   ├── AllocationTarget.java
│   │   │   └── FundamentalScore.java
│   │   └── scheduler/
│   │       ├── DcaScheduler.java
│   │       ├── RebalanceScheduler.java
│   │       ├── DividendCheckScheduler.java
│   │       └── FundamentalUpdateScheduler.java
│   │
│   ├── risk/
│   │   ├── RiskManager.java               // Pool-aware AND market-aware
│   │   ├── PositionSizer.java             // Currency-aware + settlement-aware
│   │   ├── PortfolioTracker.java          // Multi-currency valuation
│   │   ├── CircuitBreaker.java
│   │   ├── StopLossMonitor.java           // Runs during BOTH market hours
│   │   ├── PoolAllocationChecker.java
│   │   ├── CurrencyExposureChecker.java   // max 70% in any currency
│   │   ├── GeographicExposureChecker.java // max 75% in any single market
│   │   ├── SettlementCashTracker.java     // ★★ CRITICAL — available vs settling cash
│   │   └── entity/
│   │       ├── Position.java              // Has market, currency, fx_rate_at_entry
│   │       ├── PortfolioSnapshot.java
│   │       └── RiskCheckResult.java
│   │
│   ├── execution/
│   │   ├── TroveBrowserAgent.java        // Single Playwright agent for NGX + US
│   │   ├── TroveApiClient.java           // ★ STUB — future API migration
│   │   ├── BrokerGateway.java             // ★ Interface — Playwright now, API later
│   │   ├── BrowserSessionLock.java        // ★★ CRITICAL — serializes all Playwright ops
│   │   ├── OtpHandler.java                // ★★ CRITICAL — handles Trove 2FA via WhatsApp
│   │   ├── OrderRecoveryService.java      // ★★ CRITICAL — handles mid-order failures
│   │   ├── OrderRouter.java               // Routes by market: NGX vs US order flows
│   │   ├── OrderManager.java
│   │   ├── OrderVerifier.java
│   │   ├── ScreenshotService.java
│   │   └── entity/
│   │       ├── TradeOrder.java            // Has market, currency, fx_rate, recovery fields
│   │       └── OrderStatus.java           // Includes UNCERTAIN state
│   │
│   ├── reconciliation/                     // ★★ NEW — STATE SYNCHRONIZATION MODULE
│   │   ├── PortfolioReconciler.java       // Syncs DB state with Trove actual portfolio
│   │   ├── CashReconciler.java            // Syncs available + settling cash per currency
│   │   ├── ReconciliationReport.java
│   │   ├── FxRateReconciler.java          // Captures Trove ACTUAL FX rate vs market rate
│   │   └── scheduler/
│   │       └── ReconciliationScheduler.java
│   │
│   ├── backtest/                           // ★★ NEW — BACKTESTING ENGINE
│   │   ├── BacktestRunner.java            // Replays historical data through strategies
│   │   ├── BacktestConfig.java
│   │   ├── SimulatedOrderExecutor.java    // Fills at historical prices with slippage
│   │   ├── PerformanceAnalyzer.java       // Win rate, Sharpe, drawdown, profit factor
│   │   ├── BacktestReport.java
│   │   └── controller/
│   │       └── BacktestController.java    // REST API to trigger and view backtests
│   │
│   ├── fx/                                 // ★★ CURRENCY MANAGEMENT MODULE
│   │   ├── FxRateService.java             // Current + historical rates
│   │   ├── FxConverter.java               // NGN<->USD conversion
│   │   ├── TroveFxRateCapture.java       // ★★ Captures ACTUAL Trove conversion rate
│   │   ├── CurrencyExposureTracker.java
│   │   └── model/
│   │       └── Money.java                 // Value object: amount + currency
│   │
│   ├── notification/
│   │   ├── WhatsAppService.java
│   │   ├── TelegramService.java
│   │   ├── NotificationRouter.java
│   │   ├── TradeApprovalService.java
│   │   ├── MessageFormatter.java          // Market-aware formatting (₦ vs $)
│   │   └── controller/
│   │       └── WhatsAppWebhookController.java
│   │
│   ├── dashboard/
│   │   ├── DashboardController.java       // Multi-market portfolio views
│   │   └── PerformanceReporter.java       // Per-market, per-pool, combined
│   │
│   └── common/
│       ├── model/
│       │   ├── TradeSide.java
│       │   ├── Market.java                // Enum: NGX, US
│       │   ├── Currency.java              // Enum: NGN, USD
│       │   └── PortfolioPool.java
│       ├── util/
│       │   ├── MarketHoursUtil.java       // Multi-market: isMarketOpen(Market m)
│       │   └── MoneyFormatUtil.java       // ₦ or $ formatting based on currency
│       └── exception/
│           ├── InsufficientLiquidityException.java
│           ├── RiskLimitExceededException.java
│           ├── BrokerSessionException.java
│           ├── KillSwitchActiveException.java
│           ├── PoolAllocationExceededException.java
│           ├── CurrencyExposureExceededException.java
│           ├── OrderRecoveryException.java        // ★ NEW
│           └── ReconciliationMismatchException.java // ★ NEW
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/
│       ├── V1__create_ohlcv_table.sql
│       ├── V2__create_etf_valuation_table.sql
│       ├── V3__create_trade_order_table.sql
│       ├── V4__create_position_table.sql
│       ├── V5__create_portfolio_snapshot_table.sql
│       ├── V6__create_corporate_action_table.sql
│       ├── V7__create_fundamental_data_table.sql
│       ├── V8__create_core_holding_table.sql
│       ├── V9__create_dca_plan_table.sql
│       ├── V10__create_dividend_event_table.sql
│       ├── V11__create_rebalance_action_table.sql
│       ├── V12__add_pool_market_currency_columns.sql
│       ├── V13__create_fx_rate_table.sql
│       ├── V14__create_news_article_table.sql
│       ├── V15__create_news_event_table.sql
│       ├── V16__create_settlement_ledger_table.sql    // ★ NEW
│       ├── V17__create_reconciliation_log_table.sql   // ★ NEW
│       └── V18__add_order_recovery_columns.sql        // ★ NEW
│       └── V19__create_ai_analysis_table.sql          // ★ NEW — AI verdict audit trail
│       └── V20__create_discovery_tables.sql            // ★ NEW — Dynamic stock discovery
│
├── src/test/java/com/tradingbot/
│   ├── signal/ ...
│   ├── strategy/ ...
│   ├── longterm/ ...
│   ├── risk/
│   │   └── SettlementCashTrackerTest.java   // ★ NEW
│   ├── news/
│   │   ├── NewsEventClassifierTest.java
│   │   ├── InsiderTradeDetectorTest.java
│   │   └── NewsImpactScorerTest.java
│   ├── ai/                                    // ★ NEW
│   │   ├── AiNewsAnalyzerTest.java
│   │   ├── AiCostTrackerTest.java
│   │   └── AiFallbackHandlerTest.java
│   ├── discovery/                              // ★ NEW
│   │   ├── DiscoveryEngineTest.java
│   │   ├── WatchlistManagerTest.java
│   │   ├── PromotionPolicyTest.java
│   │   └── DemotionPolicyTest.java
│   ├── fx/
│   │   ├── FxConverterTest.java
│   │   └── CurrencyExposureTrackerTest.java
│   ├── reconciliation/                       // ★ NEW
│   │   ├── PortfolioReconcilerTest.java
│   │   └── CashReconcilerTest.java
│   ├── backtest/                             // ★ NEW
│   │   ├── BacktestRunnerTest.java
│   │   └── SimulatedOrderExecutorTest.java
│   ├── execution/                            // ★ NEW
│   │   ├── OrderRecoveryServiceTest.java
│   │   └── BrowserSessionLockTest.java
│   └── data/ ...
```

---

## CRITICAL DOMAIN KNOWLEDGE

### Market Hours (MULTI-TIMEZONE)

| Market | Timezone | Trading Hours (Local) | Trading Hours (WAT) |
|--------|----------|----------------------|---------------------|
| NGX | WAT (UTC+1) | 10:00 - 14:30 | 10:00 - 14:30 |
| US (NYSE/NASDAQ) | EST (UTC-5) | 9:30 - 16:00 | 15:30 - 22:00 |
| Gap | — | — | 14:30 - 15:30 (1 hour) |

**Bot operational window: 09:00 - 22:00 WAT (13 hours)**

```java
public boolean isMarketOpen(Market market) {
    ZonedDateTime now = ZonedDateTime.now(market.getTimezone());
    // NGX: 10:00-14:30 Africa/Lagos
    // US:  09:30-16:00 America/New_York
}
```

### NGX Market Rules
- Daily price limit: +/-10%
- Settlement: T+2 — **cash from Monday sell available Wednesday**
- ETF NAV disconnect = primary NGX edge
- All orders: LIMIT only

### US Market Rules
- No daily price limits (but circuit breakers on indices: 7%, 13%, 20% drops)
- Settlement: T+1 — **cash from Monday sell available Tuesday**
- Extended hours via Trove (pre-market, after-hours)
- Fractional shares supported on Trove
- US dividends: **30% withholding tax** for Nigerian residents (no tax treaty)
- **Freeriding violation:** Buying with unsettled funds and selling before funds settle = broker may freeze account

### Trove-Specific
- **Web portal:** app.trovefinance.com (full JavaScript SPA — confirmed working for retail users)
- NGX trades via Lambeth Capital (NGX Trading License Holder)
- US trades via DriveWealth LLC (FINRA/SIPC member)
- Commission: ~1.5% per US trade, ~0.5% + N100 for NGX trades
- **Trove applies its own FX spread** when converting NGN to USD — typically 2-5% above market rate
- Naira deposits converted to USD internally for US trades
- NGX and US portfolios visible in single dashboard
- **EODHD ticker format:** NGX = `SYMBOL.XNSA`, US = `SYMBOL.US`
- **OTP/2FA likely required** on login from server/headless environments

### Settlement Rules — CRITICAL

**This is the #1 cause of rejected orders and account freezes. Get this right.**

- **NGX:** T+2 settlement. Sell ZENITHBANK Monday → cash available Wednesday.
- **US:** T+1 settlement. Sell VOO Monday → cash available Tuesday.
- **Freeriding (US):** Buy with unsettled funds then sell before settlement → broker freezes account for 90 days.
- **PositionSizer MUST use `available_cash`, NEVER `total_cash` (available + settling).**
- **SettlementCashTracker maintains a per-market, per-currency ledger of settling amounts and their settlement dates.**
- On each settlement date, settling cash automatically transitions to available cash.
- Every sell order creates a settlement_ledger entry with `settles_on` date.
- `getAvailableCash(Market, Currency)` = total_cash - sum(unsettled amounts for that market/currency).

### FX Considerations — DUAL RATE TRACKING

- USD/NGN rate is embedded in every cross-currency portfolio calculation
- **TWO rates tracked for every US trade:**
  1. `market_fx_rate` — from EODHD or CBN (what the open market says)
  2. `broker_fx_rate` — from Trove's transaction confirmation page (what you ACTUALLY paid)
- **Trove's rate DIFFERS from market rate** — Trove charges a spread (typically 2-5%)
- **Use Trove's actual rate for P&L calculations** — this is the real cost basis
- **Track FX spread cost separately** — it's a line item in performance reporting
- TroveFxRateCapture scrapes the actual rate from the transaction/receipt page after every US trade or DCA execution
- A US stock up 10% in USD but naira weakened 5% = 15.5% NGN return
- All portfolio rollup to NGN base currency

---

## CONFIGURATION PROPERTIES (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/trading_bot
    username: ${DB_USERNAME:trader}
    password: ${DB_PASSWORD:changeme}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

eodhd:
  api-key: ${EODHD_API_KEY}
  base-url: https://eodhd.com/api

markets:
  ngx:
    timezone: Africa/Lagos
    open: "10:00"
    close: "14:30"
    eodhd-exchange: XNSA
    daily-price-limit-pct: 10.0
    settlement-days: 2
    currency: NGN
  us:
    timezone: America/New_York
    open: "09:30"
    close: "16:00"
    eodhd-exchange: US
    settlement-days: 1
    currency: USD

trading:
  base-currency: NGN
  watchlist:
    ngx-etfs: STANBICETF30,VETGRIF30,MERGROWTH,MERVALUE,SIAMLETF40,NEWGOLD
    ngx-large-caps: ZENITHBANK,GTCO,ACCESSCORP,UBA,FBNH,DANGCEM,BUACEMENT,SEPLAT,ARADEL,MTNN
    us-core-etfs: VOO,QQQ,SCHD,VYM,BND,GLD,VNQ,VXUS
    us-sectors: XLF,XLK,XLE,XLV,XLI,XLC

# ─── RISK MANAGEMENT ───
risk:
  max-open-positions: 20

  satellite:
    max-risk-per-trade-pct: 2.0
    max-single-position-pct: 15.0
    max-sector-exposure-pct: 40.0
    daily-loss-circuit-breaker-pct: 5.0
    weekly-loss-circuit-breaker-pct: 10.0
    min-cash-reserve-pct: 10.0
    min-avg-daily-volume: 10000        # NGX minimum
    min-avg-daily-volume-us: 100000    # US minimum (more liquid)
    max-volume-participation-pct: 10.0

  core:
    max-single-position-pct: 20.0
    max-sector-exposure-pct: 45.0
    stop-loss-pct: 25.0
    min-cash-reserve-pct: 5.0
    min-dividend-yield-pct: 4.0        # For NGX core
    min-dividend-yield-us-pct: 2.0     # US yields are lower (but taxed 30%)
    min-fundamental-score: 60

  pool:
    core-target-pct: 65.0
    core-min-pct: 55.0
    core-max-pct: 75.0
    satellite-target-pct: 35.0
    satellite-min-pct: 25.0
    satellite-max-pct: 45.0
    rebalance-drift-threshold-pct: 10.0

  exposure:
    max-single-currency-pct: 70.0      # Never >70% in NGN or USD
    max-single-market-pct: 75.0        # Never >75% in NGX or US
    target-ngx-pct: 50.0
    target-us-pct: 50.0

# ─── TROVE BROKER ───
trove:
  base-url: https://app.trovefinance.com
  username: ${TROVE_USERNAME}
  password: ${TROVE_PASSWORD}
  headless: true
  slow-mo-ms: 500
  screenshot-dir: ./screenshots
  session-max-hours: 5
  order-confirmation-delay-seconds: 3

  # OTP / 2FA handling
  otp:
    enabled: true
    method: WHATSAPP_PROMPT           # Bot sends you a WhatsApp asking for OTP, waits for reply
    timeout-seconds: 120              # Max 2 min to enter OTP before aborting
    max-retries: 2                    # Retry login + OTP up to 2 times

  # Selectors are PLACEHOLDERS — discover real ones with: playwright codegen https://app.trovefinance.com
  selectors:
    login-email: "input[name='email']"
    login-password: "input[type='password']"
    login-submit: "button[type='submit']"
    otp-input: "input[name='otp']"
    otp-submit: "button:has-text('Verify')"
    # NGX trading
    ngx-trade-tab: "[data-tab='nigerian']"
    ngx-symbol-search: "input.stock-search"
    ngx-quantity: "input[name='quantity']"
    ngx-price: "input[name='price']"
    ngx-buy-button: "button:has-text('Buy')"
    ngx-sell-button: "button:has-text('Sell')"
    ngx-review: "button:has-text('Review')"
    ngx-confirm: "button:has-text('Confirm')"
    # US trading
    us-trade-tab: "[data-tab='us']"
    us-symbol-search: "input.us-stock-search"
    us-amount-or-shares: "input[name='amount']"
    us-order-type: "select[name='orderType']"    # Market, Limit, Stop
    us-limit-price: "input[name='limitPrice']"
    us-buy-button: "button:has-text('Buy')"
    us-sell-button: "button:has-text('Sell')"
    us-review: "button:has-text('Review')"
    us-confirm: "button:has-text('Confirm')"
    # Portfolio
    portfolio-tab: "[data-tab='portfolio']"
    portfolio-ngx-section: ".ngx-holdings"
    portfolio-us-section: ".us-holdings"
    # Transaction history (for FX rate capture + reconciliation)
    transactions-tab: "[data-tab='transactions']"
    transaction-fx-rate: ".fx-rate"
    order-history: ".order-history"

  # Future API migration
  api:
    enabled: false                      # Set to true when API access is granted
    base-url: https://api.trovefinance.com
    api-key: ${TROVE_API_KEY:}
    api-secret: ${TROVE_API_SECRET:}

# ─── RECONCILIATION ───
reconciliation:
  run-on-startup: true                  # ALWAYS reconcile on boot
  daily-cron: "0 0 9 * * MON-FRI"     # Also reconcile daily before any trading
  mismatch-action: ALERT_AND_HALT      # HALT trading on position mismatch until resolved
  position-tolerance-shares: 0          # Zero tolerance — exact match required
  cash-tolerance-pct: 1.0              # 1% tolerance on cash (rounding)

# ─── BACKTESTING ───
backtest:
  default-slippage-pct: 0.1            # 0.1% assumed slippage per trade
  ngx-commission-pct: 0.5
  us-commission-pct: 1.5
  default-start-capital-ngn: 5000000
  default-start-capital-usd: 3000

# ─── NEWS INTELLIGENCE ───
news:
  enabled: true
  scrape-interval-minutes: 15
  sources:
    ngx:
      - name: Nairametrics
        url: https://nairametrics.com/feed/
        type: RSS
      - name: BusinessDay
        url: https://businessday.ng/feed/
        type: RSS
      - name: NGX Daily Bulletin
        url: https://ngxgroup.com/exchange/trade/equities/daily-bulletin/
        type: PDF_SCRAPE
      - name: CBN Press
        url: https://www.cbn.gov.ng/press/
        type: HTML_SCRAPE
    us:
      - name: Reuters Business
        url: https://www.reuters.com/business/rss
        type: RSS
      - name: Seeking Alpha
        url: https://seekingalpha.com/market-news
        type: HTML_SCRAPE
  insider-trade:
    min-value-ngn: 10000000            # N10M minimum insider purchase to flag
    lookback-days: 30
  event-classification:
    confidence-threshold: 0.7
  signal-weight-pct: 10               # News = max 10% of composite signal

# ─── AI INTELLIGENCE LAYER (Claude API) ───
ai:
  enabled: true
  api-key: ${ANTHROPIC_API_KEY}
  base-url: https://api.anthropic.com/v1

  # Model tiering for cost control
  models:
    bulk: claude-haiku-4-5-20251001     # Cheap: every article gets analyzed (~$0.25/1M input tokens)
    deep: claude-sonnet-4-5-20250929    # Smart: only HIGH_IMPACT or low-confidence events (~$3/1M input)
    # Opus reserved for manual/on-demand analysis only — never automated

  # Cost guardrails
  cost:
    daily-budget-usd: 0.50              # Hard daily cap — stops AI calls when exceeded
    monthly-budget-usd: 10.00           # Monthly cap — alerts at 80%, stops at 100%
    max-input-tokens-per-article: 2000  # Truncate long articles before sending
    max-output-tokens: 500              # Keep responses concise
    alert-at-pct: 80                    # WhatsApp alert at 80% of budget

  # Analysis controls
  analysis:
    process-delay-seconds: 5            # Rate limit: gap between API calls
    batch-size: 10                      # Process up to 10 articles per batch run
    deep-analysis-triggers:             # Events that escalate Haiku → Sonnet
      - EARNINGS_BEAT
      - EARNINGS_MISS
      - DIVIDEND_CUT
      - MERGER_ACQUISITION
      - REGULATORY_ACTION
      - SECTOR_POLICY
      - FED_RATE_DECISION
    confidence-escalation-threshold: 0.5  # If Haiku confidence < 50%, re-analyze with Sonnet
    earnings-transcript-enabled: true     # Use Sonnet for earnings call transcript analysis
    cross-article-synthesis-enabled: true # Daily synthesis of all articles per stock

  # Graceful degradation
  fallback:
    on-api-error: USE_RULE_BASED_ONLY   # If Claude API is down, fall back silently
    on-budget-exceeded: USE_RULE_BASED_ONLY
    max-retries: 2
    retry-delay-seconds: 10

# ─── STOCK DISCOVERY ───
discovery:
  enabled: true
  max-active-watchlist-size: 60        # SEED + PROMOTED combined cap
  max-observation-slots: 20            # Max stocks in OBSERVATION at once
  demoted-cooldown-days: 90            # Days before re-discovery after demotion
  demotion-check-cron: "0 0 7 * * MON-FRI"  # Daily check for demotion criteria

  screener:
    ngx:
      enabled: true
      cron: "0 0 18 * * SUN"           # Weekly Sunday 18:00 WAT
      min-market-cap-ngn: 5000000000   # N5B minimum
      min-avg-volume: 50000
      min-eps: 0.01                    # Must be profitable
      max-results: 30
    us:
      enabled: true
      cron: "0 0 20 * * SUN"           # Weekly Sunday 20:00 WAT
      min-market-cap-usd: 2000000000   # $2B minimum
      min-avg-volume: 500000
      min-eps: 0.01
      max-results: 30
      allowed-sectors:                  # Only discover in these sectors
        - Technology
        - Healthcare
        - Financial Services
        - Consumer Cyclical
        - Industrials
        - Energy
        - Communication Services

  observation:
    min-days-ngx: 7                    # Minimum observation before promotion
    min-days-us: 5
    max-days-before-demotion: 30       # Auto-demote if not promoted in 30 days
    require-buy-signal: true           # Must get ≥1 BUY signal to promote
    min-fundamental-score: 50

  news-discovery:
    enabled: true                       # Create candidates from news about unknown stocks
    fast-track-events:                  # These event types skip to OBSERVATION immediately
      - EARNINGS_BEAT
      - MERGER_ACQUISITION
      - INSIDER_BUYING
      - BUYBACK

  ai-assessment:
    enabled: true                       # Use Haiku to score candidates (Stage 3)
    min-score-to-observe: 40           # AI score < 40 → demote
    fast-track-score: 60               # AI score > 60 → bonus points

# ─── LONG-TERM PORTFOLIO ───
longterm:
  core:
    target-allocations:
      # NGX Core (50% of core)
      ZENITHBANK: 10.0
      GTCO: 8.0
      ACCESSCORP: 5.0
      UBA: 4.0
      DANGCEM: 6.0
      SEPLAT: 5.0
      MTNN: 7.0
      NEWGOLD: 5.0
      # US Core (50% of core)
      VOO: 15.0         # S&P 500
      SCHD: 10.0        # US high dividend
      BND: 8.0          # US bonds (stability)
      GLD: 5.0          # Gold (currency hedge)
      VXUS: 7.0         # International ex-US
      VNQ: 5.0          # US REITs

  dca:
    enabled: true
    ngx-budget-naira-monthly: 150000
    us-budget-usd-monthly: 300
    ngx-execution-day: 5
    us-execution-day: 10             # Different day — spread FX conversion load
    fallback-day-if-weekend: NEXT
    top-up-on-dip-pct: 10.0

  dividend:
    reinvest: true
    reinvest-into: SAME_STOCK
    track-ex-dates: true
    alert-days-before-ex-date: 7
    us-withholding-tax-pct: 30.0     # Nigerian residents: 30% US dividend tax

  rebalance:
    frequency: QUARTERLY
    drift-threshold-pct: 10.0
    method: THRESHOLD
    use-new-cash-first: true
    require-approval: true

# ─── STRATEGY CONFIGS ───
strategies:
  # Satellite (short-term)
  etf-nav-arbitrage:
    enabled: true
    pool: SATELLITE
    market: NGX
    entry-discount-pct: 10.0
    exit-premium-pct: 20.0
    extreme-premium-pct: 50.0
    max-rsi: 60
    min-volume-ratio: 1.2
  momentum-breakout:
    enabled: true
    pool: SATELLITE
    market: BOTH
    volume-spike-ratio: 3.0
    min-rsi: 40
    max-rsi: 65
    sma-period: 20
    atr-stop-multiplier: 2.0
  us-earnings-momentum:
    enabled: true
    pool: SATELLITE
    market: US
    buy-window-days-after-earnings: 3
    min-eps-surprise-pct: 5.0
    min-revenue-surprise-pct: 2.0
  us-etf-rotation:
    enabled: true
    pool: SATELLITE
    market: US
    rotation-frequency: MONTHLY
    top-n-sectors: 3

  # Core (long-term)
  dividend-accumulation:
    enabled: true
    pool: CORE
    market: BOTH
    ngx-min-trailing-yield-pct: 6.0
    us-min-trailing-yield-pct: 2.5   # Lower because US yields are lower
  value-accumulation:
    enabled: true
    pool: CORE
    market: BOTH
  dca:
    enabled: true
    pool: CORE
    market: BOTH
  currency-hedge:
    enabled: true
    pool: CORE
    market: BOTH
    gold-target-pct: 10.0
    naira-weakness-threshold-30d-pct: 5.0
  sector-rotation:
    enabled: true
    pool: CORE
    market: BOTH
  pension-flow:
    enabled: true
    pool: BOTH
    market: NGX

notification:
  whatsapp:
    enabled: true
    waha-base-url: http://localhost:3000
    waha-session: default
    chat-id: ${WHATSAPP_CHAT_ID}
  telegram:
    enabled: true
    bot-token: ${TELEGRAM_BOT_TOKEN}
    chat-id: ${TELEGRAM_CHAT_ID}
  approval:
    timeout-minutes: 5
    default-on-timeout: REJECT
```

---

## NEWS INTELLIGENCE MODULE

### Architecture: Signal Modifier, NOT Signal Generator

News is capped at **10% of composite signal weight**. It boosts or dampens signals from technical/fundamental analysis. It does NOT generate standalone trade signals.

```
CompositeSignalScorer:
  +-- TechnicalIndicators    (weight: 40%)
  +-- FundamentalScore       (weight: 30%)
  +-- NAV Discount (NGX ETFs)(weight: 20%)
  +-- NewsImpactScore        (weight: 10%)  <-- capped
      ├── Rule-Based (Level 2)   50% of news weight
      └── AI Analysis (Level 3)  50% of news weight (falls back to 100% rule-based)
```

### Level 1: News Radar (Keyword Alerting)

Scrape RSS feeds every 15 minutes. Match headlines against watchlist symbols. Alert immediately via WhatsApp.

```
SOURCES (NGX):
  - Nairametrics RSS -> every 15 min, 7 AM - 5 PM WAT
  - BusinessDay RSS -> every 15 min, 7 AM - 5 PM WAT
  - NGX Daily Bulletin PDF -> once daily, 5 PM WAT
  - CBN Press Releases -> every 30 min, 8 AM - 6 PM WAT

SOURCES (US):
  - Reuters Business RSS -> every 15 min, 2 PM - 11 PM WAT
  - Seeking Alpha -> every 30 min, 3 PM - 11 PM WAT

MATCHING: Simple keyword match -- symbol name OR company name in headline/title.
  "ZENITHBANK" OR "Zenith Bank" -> match
  "AAPL" OR "Apple" -> match (but filter out "apple fruit" via context — require financial section)

OUTPUT: Immediate WhatsApp alert:
  "NEWS: Nairametrics mentions ZENITHBANK at 7:15 AM WAT
   'Zenith Bank declares N3.50 final dividend for FY2025'
   Market opens in 2h 15m"
```

### Level 2: Event Classification (Rule-Based)

Parse article headline + first paragraph. Classify into known event types using regex patterns. Apply impact rules.

```java
public enum EventType {
    EARNINGS_BEAT,           // "profit rose", "earnings above", "revenue growth"
    EARNINGS_MISS,           // "profit declined", "earnings below", "revenue fell"
    DIVIDEND_DECLARATION,    // "declares dividend", "final dividend of", "interim dividend"
    DIVIDEND_CUT,            // "reduces dividend", "suspends dividend", "no dividend"
    CEO_CHANGE,              // "appoints new CEO", "CEO resigns", "CEO steps down"
    REGULATORY_ACTION,       // "CBN penalizes", "SEC sanctions", "fined"
    RIGHTS_ISSUE,            // "rights issue", "rights offering"
    MERGER_ACQUISITION,      // "acquires", "merger", "takeover bid"
    CREDIT_RATING_CHANGE,    // "upgrades rating", "downgrades rating", "rating watch"
    INSIDER_BUYING,          // From NGX bulletin Form 29 parsing
    INSIDER_SELLING,         // From NGX bulletin Form 29 parsing
    SECTOR_POLICY,           // "CBN raises rates", "subsidy removal", "forex policy"
    FED_RATE_DECISION,       // "Fed holds rates", "Fed raises", "FOMC"
    US_EARNINGS_SURPRISE,    // "beats estimates", "EPS surprise", "revenue beat"
    STOCK_SPLIT,             // "stock split", "share split"
    BUYBACK,                 // "share buyback", "repurchase program"
    MACRO_EVENT              // GDP data, inflation data, employment data
}
```

### Event Impact Rules

| Event Type | Market | Signal Modifier | Action |
|---|---|---|---|
| EARNINGS_BEAT | BOTH | +15% confidence | Boost buy signals |
| EARNINGS_MISS | BOTH | -20% confidence | Dampen buy, boost sell |
| DIVIDEND_DECLARATION | BOTH | +10% core confidence | Trigger dividend strategy |
| DIVIDEND_CUT | BOTH | -30% confidence, CORE ALERT | Review core position |
| CEO_CHANGE | BOTH | 0% (neutral) | Alert only — too ambiguous |
| REGULATORY_ACTION | BOTH | -15% confidence | Tighten stops |
| RIGHTS_ISSUE | NGX | -10% confidence | Flag dilution risk |
| MERGER_ACQUISITION | BOTH | +20% for target | Momentum boost |
| INSIDER_BUYING (>N10M) | NGX | +15% confidence | Strong conviction signal |
| INSIDER_SELLING | NGX | 0% | Alert only |
| SECTOR_POLICY | BOTH | Variable | Trigger sector rotation review |
| FED_RATE_DECISION | US | Variable | Impact US bond + bank positions |
| US_EARNINGS_SURPRISE | US | +15% satellite confidence | UsEarningsMomentum trigger |
| BUYBACK | US | +10% confidence | Positive for price support |

### NGX Daily Bulletin Parser (Form 29 — Insider Trades)

```
CLASS: InsiderTradeDetector
SOURCE: NGX Daily Bulletin PDF (published ~5 PM WAT daily)
URL: https://ngxgroup.com/exchange/trade/equities/daily-bulletin/
PARSE: Download PDF -> Apache PDFBox text extraction -> scan for Form 29 section
  -> extract person, company, shares, value, date
  -> if BUY and value > N10M within 30 days -> INSIDER_BUYING event
OUTPUT: NewsEvent entity + WhatsApp alert with details.
```

### Level 3: AI-Powered Deep Analysis (Claude API)

Level 3 enriches Level 1+2 outputs — it does NOT replace them. Every article first gets rule-based classification (instant, free, deterministic). Then AI adds nuance that regex cannot capture.

```
FLOW:
  Article scraped (Level 1)
    → Rule-based classification (Level 2) — immediate, free
    → Queued for AI analysis (Level 3) — async, batched every 15 min
    → AI result stored in ai_analysis table
    → NewsImpactScorer blends BOTH scores

TIERED MODEL STRATEGY (cost control):
  ┌──────────────────────────────────────────────────────────────────┐
  │                                                                  │
  │  EVERY article ──→ Haiku ($0.25/1M tokens)                     │
  │    Cost: ~$0.0001 per article (~200 articles/day = $0.02/day)  │
  │    Output: sentiment (-1 to +1), confidence (0-1),              │
  │            3-5 key factors, market impact assessment             │
  │                                                                  │
  │  IF event is HIGH_IMPACT (earnings, M&A, regulatory, rate       │
  │     decisions, dividend cuts) OR Haiku confidence < 50%:        │
  │                                                                  │
  │  ESCALATE ──→ Sonnet ($3/1M tokens)                            │
  │    Cost: ~$0.003 per article (~20 escalations/day = $0.06/day) │
  │    Output: deeper analysis, forward-looking implications,       │
  │            cross-reference with sector trends, recommended       │
  │            signal adjustment with reasoning                      │
  │                                                                  │
  │  DAILY estimated cost: ~$0.08-0.15/day (~$2.50-4.50/month)    │
  └──────────────────────────────────────────────────────────────────┘
```

#### AiNewsAnalyzer — Article-Level Analysis

```java
CLASS: AiNewsAnalyzer

INPUT: NewsArticle (headline + first 500 words) + EventType from Level 2
OUTPUT: AiAnalysis entity

HAIKU PROMPT (system):
  "You are a financial analyst specializing in Nigerian (NGX) and US stock markets.
   Analyze this news article for trading impact. Be concise and precise.
   Respond ONLY in JSON format."

HAIKU PROMPT (user):
  "Article: {headline}\n{body_truncated_to_500_words}
   Rule-based classification: {event_type}
   Affected stock(s): {symbols}
   Market: {NGX|US}

   Respond in JSON:
   {
     \"sentiment\": <float -1.0 to 1.0>,
     \"confidence\": <float 0.0 to 1.0>,
     \"signal_modifier_pct\": <float -30 to +30>,
     \"key_factors\": [\"factor1\", \"factor2\", \"factor3\"],
     \"nuance\": \"<one sentence the regex classifier would miss>\",
     \"contradicts_rule_based\": <boolean>,
     \"rule_based_event_type_correct\": <boolean>
   }"

ESCALATION TO SONNET (additional analysis):
  "Additionally assess:
   - Forward-looking implications (next 1-4 weeks)
   - How this interacts with current sector trends
   - Whether this changes the fundamental thesis
   - Specific price action expectation (bullish/bearish/neutral with timeframe)
   - Recommended signal adjustment with reasoning"

CRITICAL: Parse JSON response defensively. If malformed → fall back to rule-based score.
CRITICAL: Article body truncated to max_input_tokens_per_article (2000 tokens) to control cost.
```

#### AiEarningsAnalyzer — Earnings-Specific Deep Analysis (Sonnet Only)

```
CLASS: AiEarningsAnalyzer
TRIGGER: US_EARNINGS_SURPRISE or EARNINGS_BEAT or EARNINGS_MISS events
MODEL: Always Sonnet (earnings decisions are high-stakes)

INPUT: Earnings article + any available transcript excerpt
OUTPUT: AiAnalysis with earnings-specific fields

ADDITIONAL PROMPT FIELDS:
  "Assess:
   - Revenue quality (recurring vs one-time)
   - Forward guidance (raised/maintained/lowered)
   - Management tone (confident/cautious/defensive)
   - Margin trajectory
   - Is the headline number misleading? (e.g., beat on revenue miss, accounting adjustments)
   - Conviction level for position: STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL"
```

#### AiCrossArticleSynthesizer — Daily Stock Summary

```
CLASS: AiCrossArticleSynthesizer
SCHEDULE: Once daily at 21:00 WAT (after both markets close)
MODEL: Haiku (summarization is cheap)

INPUT: All articles mentioning stock X from today (max 10 articles, concatenated)
OUTPUT: Single synthesis per stock

PURPOSE: When 5 different sources mention ZENITHBANK today, synthesize:
  - What's the consensus? Are sources agreeing or contradicting?
  - What's the net sentiment across all coverage?
  - Any emerging narrative? (e.g., multiple analysts upgrading same sector)
  - Does today's coverage change the daily signal?

Only runs for stocks with 2+ articles in a day.
Result stored in ai_analysis with type=DAILY_SYNTHESIS.
```

#### AiInsiderTradeInterpreter — Pattern Analysis

```
CLASS: AiInsiderTradeInterpreter
TRIGGER: After InsiderTradeDetector finds Form 29 entries
MODEL: Haiku (pattern matching is simple)

INPUT: Insider trade data + 30-day insider trade history for same company
OUTPUT: Pattern assessment

PURPOSE: Catches what simple thresholds miss:
  - "3 directors bought in same week" → coordinated buying (very bullish)
  - "CFO sold 80% of holdings" → much more significant than "director sold 5%"
  - "Insider bought right before known catalyst (AGM, earnings)" → informed buying
  - Cross-company: "insiders buying across 3 banks simultaneously" → sector signal
```

#### AiCostTracker — Budget Enforcement

```
CLASS: AiCostTracker

TRACKS: Input tokens, output tokens, model used, cost per call
STORES: Running daily + monthly totals
ENFORCES:
  - daily-budget-usd: hard stop — no more AI calls today (fall back to rule-based)
  - monthly-budget-usd: hard stop at 100%, WhatsApp alert at 80%
REPORTS: Monthly cost breakdown in performance report

IMPLEMENTATION:
  @Service
  public class AiCostTracker {
      // Anthropic returns token counts in response headers/body
      // Pricing: Haiku input=$0.25/1M, output=$1.25/1M
      //          Sonnet input=$3/1M, output=$15/1M
      // Calculate cost per call, accumulate into daily/monthly counters
      // Before each API call: check budget → if exceeded, return empty Optional
  }
```

#### AiFallbackHandler — Graceful Degradation

```
CLASS: AiFallbackHandler

SCENARIOS WHERE AI IS UNAVAILABLE:
  1. Anthropic API returns 5xx → retry twice, then fall back
  2. Anthropic API returns 429 (rate limit) → backoff, then fall back
  3. Daily/monthly budget exceeded → fall back
  4. API key not configured (ai.enabled=false) → fall back
  5. Response JSON is malformed → fall back for that article
  6. Network timeout → fall back

FALLBACK BEHAVIOR:
  - NewsImpactScorer uses ONLY rule-based score (Level 1+2)
  - No error thrown upstream — signal generation continues normally
  - Log warning: "AI analysis unavailable for article {id}, using rule-based only"
  - Track fallback count in daily metrics
  - If fallback rate > 50% for a day → WhatsApp alert

CRITICAL: The bot MUST function identically without AI. AI is enrichment, not dependency.
```

### Updated CompositeSignalScorer (with AI)

```
CompositeSignalScorer (UPDATED):
  +-- TechnicalIndicators    (weight: 40%)  — unchanged
  +-- FundamentalScore       (weight: 30%)  — unchanged
  +-- NAV Discount (NGX ETFs)(weight: 20%)  — unchanged
  +-- NewsImpactScore        (weight: 10%)  <-- NOW BLENDED:
      │
      ├── Rule-Based Score (Level 2)    — weight: 50% of news component
      └── AI Analysis Score (Level 3)   — weight: 50% of news component
          (falls back to 100% rule-based if AI unavailable)

NET EFFECT: AI controls 5% of total signal weight (50% of 10%).
This means even a catastrophically wrong AI analysis can only shift
the total signal by ±5%. Combined with approval flow for satellite
trades, the blast radius of AI mistakes is tightly contained.

BLENDING LOGIC:
  if (aiAnalysis.isPresent() && aiAnalysis.confidence > 0.3) {
      newsScore = (ruleBased * 0.5) + (aiScore * 0.5);
      // If AI contradicts rule-based with high confidence, flag for review
      if (aiAnalysis.contradictsRuleBased && aiAnalysis.confidence > 0.8) {
          alert("AI disagrees with rule-based for {symbol}: {reasoning}");
      }
  } else {
      newsScore = ruleBased;  // Pure rule-based fallback
  }
```

---

## STOCK DISCOVERY MODULE — DYNAMIC WATCHLIST EXPANSION

### Why This Exists

The static watchlist (16 NGX + 14 US tickers) limits the bot to a fixed universe. Discovery enables the bot to find opportunities across the broader market — new breakouts, undervalued companies, insider accumulation, or sector momentum — without manual research. Strategies then evaluate discovered stocks with the same signal pipeline as seed stocks.

### Architecture: Discover → Observe → Promote → Trade

```
Discovery Pipeline:

  SOURCES:                         PIPELINE:                       OUTCOME:
  ┌──────────────────┐           ┌───────────────┐
  │ EODHD Screener   │──weekly──→│               │
  │ (NGX + US)       │           │               │              ┌──────────┐
  ├──────────────────┤           │   CANDIDATE   │──observe──→  │OBSERVATION│──promote──→ PROMOTED
  │ News Mentions    │──realtime→│   EVALUATOR   │   (7-14 days)│(data pull)│  (if criteria met)
  │ (unknown stocks) │           │               │              └──────────┘
  ├──────────────────┤           │               │                    │
  │ Insider Buying   │──daily──→ │               │              demote (if criteria fail after 30 days)
  │ (cross-market)   │           └───────────────┘                    │
  └──────────────────┘                                           ┌──────────┐
                                                                 │ DEMOTED  │ (stop data pull, archive)
                                                                 └──────────┘
```

### Watchlist Categories

```java
public enum WatchlistStatus {
    SEED,           // Original YAML watchlist — PERMANENT, never demoted
    CANDIDATE,      // Just discovered — needs evaluation
    OBSERVATION,    // Passed initial filters — pulling OHLCV data, building history
    PROMOTED,       // Meets all criteria — strategies can trade it
    DEMOTED         // Failed observation or lost criteria — data pull stops
}
```

**SEED stocks are PERMANENT.** The YAML watchlist (`ngx-etfs`, `ngx-large-caps`, `us-core-etfs`, `us-sectors`) is always active. Discovery only ADDS to this, never removes from it.

### Source 1: EODHD Screener API (Weekly Scan)

```
CLASS: EodhdScreenerClient
ENDPOINT: GET https://eodhd.com/api/screener?api_token={key}&filters=[...]&sort=...&limit=...
COST: 5 API calls per request (from EODHD quota, not monetary)

NGX SCAN (exchange=XNSA):
  Filters:
    - market_capitalization > 5,000,000,000 (N5B minimum — no penny stocks)
    - earnings_share > 0 (profitable companies only)
    - volume > 50,000 (minimum daily liquidity)
  Signals: new_50d_hi, new_200d_hi (breakout candidates)
  Limit: 30 results per scan
  Frequency: Weekly (Sunday 18:00 WAT)

US SCAN (exchange=US, split NYSE + NASDAQ):
  Filters:
    - market_capitalization > 2,000,000,000 ($2B minimum)
    - earnings_share > 0
    - volume > 500,000
  Optional sector filter: rotate through sectors weekly to broaden coverage
  Signals: new_50d_hi, new_200d_hi, bookvalue_negative (deep value/turnaround)
  Limit: 30 results per scan
  Frequency: Weekly (Sunday 20:00 WAT)

POST-FILTER (in application, not API):
  - Exclude stocks already in watchlist (SEED, OBSERVATION, or PROMOTED)
  - Exclude stocks in DEMOTED within last 90 days (cooldown)
  - Exclude ADRs, SPACs, blank-check companies
  - For NGX: require stock to be in NGX30 or NGX50 index, OR have >N10B market cap
  - For US: require sector to be in configured allowed-sectors list
```

### Source 2: News-Driven Discovery

```
CLASS: NewsDiscoveryListener
TRIGGER: Listens to NewsEventClassifier output

When a news article mentions a stock NOT in the current watchlist:
  1. Extract symbol from article
  2. Check: is it a known exchange? (XNSA or US)
  3. Check: does it meet minimum market cap + volume thresholds?
  4. If yes → create CANDIDATE with discovery_source = 'NEWS'
  5. If the news event is HIGH_IMPACT (earnings beat, M&A, insider buying) → fast-track to OBSERVATION

This catches opportunities from breaking news — "Company X announces $500M acquisition"
where X isn't in our watchlist but should be evaluated.
```

### Source 3: Cross-Market Insider Accumulation

```
CLASS: InsiderTradeDetector (existing, extended)

When InsiderTradeDetector processes NGX Daily Bulletin:
  - If insider buying detected for a stock NOT in watchlist:
    → Create CANDIDATE with discovery_source = 'INSIDER_BUYING'
    → Fast-track to OBSERVATION (insider buying is high-conviction signal)

For US (via EODHD insider transactions API if available):
  - Same logic: significant insider buying in unlisted stock → CANDIDATE
```

### CandidateEvaluator — Multi-Stage Scoring

```
CLASS: CandidateEvaluator

When a CANDIDATE is created, score it:

STAGE 1 — BASIC FILTERS (instant, free):
  ✓ Market cap above minimum (N5B NGX / $2B US)
  ✓ Positive EPS
  ✓ Adequate daily volume (50K NGX / 500K US)
  ✓ Not in excluded sectors (if configured)
  ✓ Not a SPAC, ADR, or holding company shell
  → If fails any → status = DEMOTED (reason logged)

STAGE 2 — FUNDAMENTAL CHECK (EODHD Fundamentals API, 1 API call):
  ✓ P/E ratio < 50 (not absurdly overvalued)
  ✓ Revenue growth > 0% (not shrinking)
  ✓ Debt-to-equity < 3.0 (not overleveraged)
  → If fails → status = DEMOTED

STAGE 3 — AI ASSESSMENT (optional, Haiku, ~$0.0001):
  If ai.enabled and ai discovery enabled:
    Send to Haiku: "Given these fundamentals for {symbol}, is this
    a reasonable addition to a diversified NGX/US portfolio? Score 0-100."
    → If AI score < 40 → DEMOTED with AI reasoning logged
    → If AI score > 60 → bonus points, fast-track promotion

OUTCOME:
  Passes all stages → status = OBSERVATION
  DataCollectionScheduler starts pulling OHLCV for this stock
```

### Observation Period

```
OBSERVATION PERIOD:
  - Duration: 7 days minimum (NGX), 5 days minimum (US)
  - Purpose: build enough OHLCV history for technical indicators (RSI needs 14 bars)
  - During observation: data is collected, signals are generated but NOT traded
  - Stock visible in dashboard with "OBSERVING" badge

WHAT'S MONITORED DURING OBSERVATION:
  - Price action: is it stable or volatile?
  - Volume consistency: was the screener catch a one-day spike?
  - News flow: any red flags (regulatory action, fraud allegations)?
  - Signal quality: are strategies generating positive signals?
```

### PromotionPolicy — When to Activate

```
CLASS: PromotionPolicy

A stock in OBSERVATION is promoted to PROMOTED (tradeable) when ALL conditions are met:

  1. Minimum observation period completed (7 days NGX, 5 days US)
  2. At least 1 strategy has generated a BUY signal during observation
  3. Average daily volume during observation > minimum threshold
  4. No REGULATORY_ACTION or DIVIDEND_CUT news events during observation
  5. Fundamental score > 50 (from FundamentalScorer)
  6. Total PROMOTED + SEED stocks < max_active_watchlist_size (default: 60)

ON PROMOTION:
  - WhatsApp notification: "DISCOVERY: {symbol} ({market}) promoted to active watchlist.
    Reason: {discovery_source}. Fundamental score: {score}. First signal: {strategy_name}."
  - Stock now eligible for all matching strategies
  - Logged in discovery_events table

IF PROMOTION CRITERIA NOT MET AFTER 30 DAYS:
  - Auto-demote to DEMOTED
  - Stop OHLCV data collection
  - 90-day cooldown before it can be re-discovered
```

### DemotionPolicy — When to Remove

```
CLASS: DemotionPolicy

A PROMOTED stock is demoted back to DEMOTED when ANY condition is met:

  1. Average daily volume drops below minimum for 10 consecutive days
  2. Fundamental score drops below 30
  3. No strategy has generated any signal (buy or sell) for 60 days (irrelevant stock)
  4. REGULATORY_ACTION event (SEC/CBN sanction, fraud, delisting warning)
  5. Stock is delisted from exchange
  6. Manual demotion via dashboard

ON DEMOTION:
  - Close any open positions in the stock (sell at market)
  - WhatsApp alert: "DISCOVERY: {symbol} demoted from active watchlist. Reason: {reason}."
  - OHLCV data collection stops
  - 90-day cooldown before re-discovery
  - Positions and trade history preserved in DB (never deleted)

CRITICAL: SEED stocks are NEVER demoted. Only PROMOTED stocks (from discovery) can be demoted.
```

### WatchlistManager — Unified Watchlist

```
CLASS: WatchlistManager

PROVIDES: getActiveWatchlist() → returns SEED + PROMOTED stocks
USED BY: DataCollectionScheduler, SignalGenerationScheduler, all Strategies

The rest of the application sees ONE unified watchlist. It does not need to know
whether a stock was manually configured or dynamically discovered.

  public List<WatchlistStock> getActiveWatchlist() {
      List<WatchlistStock> seeds = watchlistFromYaml();   // PERMANENT
      List<WatchlistStock> promoted = discoveredStockRepo
          .findByStatus(WatchlistStatus.PROMOTED);
      return Stream.concat(seeds.stream(), promoted.stream()).toList();
  }

  public List<WatchlistStock> getObservationList() {
      return discoveredStockRepo.findByStatus(WatchlistStatus.OBSERVATION);
  }

SIZE LIMITS:
  - max_active_watchlist_size: 60 (SEED + PROMOTED combined)
  - max_observation_slots: 20 (stocks currently being observed)
  - If limits reached: new candidates queue until slot opens

EODHD API COST IMPACT:
  - Each active stock = 1 OHLCV API call/day
  - 60 stocks × 1 call/day × 22 trading days = ~1,320 calls/month
  - EODHD All World plan includes 100,000 calls/month — well within budget
  - Screener scans: 2 scans/week × 5 calls = 40 calls/month (negligible)
```

---

## TRADING STRATEGIES (ALL 11)

### -- SATELLITE STRATEGIES --

**Strategy 1: ETF NAV Arbitrage** (SATELLITE, NGX)
Buy at NAV discount, sell at premium. NGX-only because US ETFs don't have NAV disconnect.
Entry: NAV discount >= 10%, RSI < 60, volume ratio > 1.2x. Exit: premium >= 20% or extreme >= 50%.

**Strategy 2: Momentum Breakout** (SATELLITE, BOTH markets)
Volume spike + price breakout. US minimum volume: 100,000 (vs 10,000 NGX).
Entry: volume > 3x avg, RSI 40-65, price > SMA(20). Exit: trailing stop at 2x ATR.

**Strategy 3: US Earnings Momentum** (SATELLITE, US)
```
CLASS: UsEarningsMomentumStrategy implements Strategy

ENTRY (ALL must be true):
  1. Company reported earnings within last 3 trading days
  2. EPS beat estimates by >= 5%
  3. Revenue beat estimates by >= 2%
  4. Stock price moved UP on earnings day (positive reaction)
  5. Post-earnings volume > 2x avg volume
  6. RSI(14) < 70
  7. NewsEventClassifier detected US_EARNINGS_SURPRISE event

EXIT:
  1. Take profit at entry + 10% OR entry + 2xATR (whichever lower)
  2. Trail stop after +5% gain
  3. Stop-loss: entry - 1.5xATR
  4. Time-based: sell after 10 trading days if neither target nor stop hit

DATA NEEDED:
  - US earnings calendar (from EODHD or financial data API)
  - Actual vs estimated EPS/revenue (from earnings data)
  - OHLCV for post-earnings price action
  - News events classified as US_EARNINGS_SURPRISE
```

**Strategy 4: US ETF Sector Rotation** (SATELLITE, US)
```
CLASS: UsEtfRotationStrategy implements Strategy

UNIVERSE: XLF, XLK, XLE, XLV, XLI, XLC, XLY, XLP, XLU, XLRE

MONTHLY SCORING:
  1. 1-month return (40%), 3-month return (30%), RSI 50-70 (15%), volume trend (15%)
  Hold top 3 sectors. Rotate on 1st trading day of month.
  Max 5% of satellite per sector ETF.
```

**Strategy 5: Pension Flow Overlay** (BOTH pools, NGX)
Position size adjuster for NGX pension-eligible stocks.

### -- CORE STRATEGIES --

**Strategy 6: Dividend Accumulation** (CORE, BOTH markets)
NGX: min 6% yield, 3yr consistency. US: min 2.5% yield (lower but taxed 30%), 10+ year growth preferred.
US effective yield = stated_yield x 0.70. NEWS BOOST: DIVIDEND_DECLARATION -> fast-track entry.

**Strategy 7: Value Accumulation** (CORE, BOTH markets)
Fundamental scoring 0-100. Same framework both markets.

**Strategy 8: Dollar-Cost Averaging** (CORE, BOTH markets)
NGX DCA: N150,000/month on 5th. US DCA: $300/month on 10th. Separate budgets, separate days. Auto-approved.

**Strategy 9: Currency Hedge** (CORE, BOTH markets)
NEWGOLD (NGX) + GLD (US). Naira weakens >5%/30d -> increase to 15%. US positions inherently hedge naira.

**Strategy 10: Sector Rotation** (CORE, BOTH markets)
Quarterly review. NGX: banking vs industrial vs oil. US: tech vs health vs financial vs energy.

**Strategy 11: Pension Flow Overlay** (BOTH, NGX only)
Position size adjuster for NGX pension-eligible stocks.

---

## RISK MANAGEMENT (POOL + MARKET + SETTLEMENT AWARE)

### Risk Dimensions

Every trade is validated across **four dimensions** before execution:

| Dimension | Checks |
|-----------|--------|
| **Pool** | CORE vs SATELLITE rules (stop width, circuit breakers, min yield) |
| **Market** | NGX vs US rules (volume minimums, daily limits, settlement days) |
| **Currency** | Max 70% in any single currency. FX-adjusted stop monitoring |
| **Settlement** | Available cash only. No spending unsettled proceeds |

### Multi-Market Risk Rules

| Rule | Value | Implementation |
|------|-------|---------------|
| Max single currency exposure | 70% | CurrencyExposureChecker — never >70% in NGN or USD |
| Max single market exposure | 75% | GeographicExposureChecker — never >75% in NGX or US |
| Target geographic split | 50/50 NGX/US | Adjustable, enforced via rebalancing |
| US dividend tax awareness | 30% | PositionSizer uses NET yield, not gross |
| FX-adjusted stops (US positions) | Both | Track stop in USD AND NGN-equivalent |
| Cross-market circuit breaker | Special | If BOTH markets down >5% same day -> global risk event alert |
| Settlement-aware sizing | Mandatory | PositionSizer queries SettlementCashTracker |

### Position Sizing (Currency + Settlement Aware)

```
// STEP 0: Get AVAILABLE cash (NOT total cash)
available_ngn = settlementCashTracker.getAvailableCash(Market.NGX, Currency.NGN)
available_usd = settlementCashTracker.getAvailableCash(Market.US, Currency.USD)

// SATELLITE NGX position sizing (in NGN)
risk_per_share = entry_price - stop_loss
max_shares_by_risk = (satellite_ngx_value * 0.02) / risk_per_share
max_shares_by_size = (satellite_ngx_value * 0.15) / entry_price
max_shares_by_volume = avg_daily_volume * 0.10
max_shares_by_cash = available_ngn / entry_price    // ★ uses AVAILABLE, not total
ngx_quantity = min(max_shares_by_risk, max_shares_by_size, max_shares_by_volume, max_shares_by_cash)

// SATELLITE US position sizing (in USD)
risk_per_share_usd = entry_price_usd - stop_loss_usd
max_shares_by_risk = (satellite_us_value_usd * 0.02) / risk_per_share_usd
max_shares_by_size = (satellite_us_value_usd * 0.15) / entry_price_usd
max_shares_by_volume = avg_daily_volume * 0.10
max_shares_by_cash = available_usd / entry_price_usd  // ★ uses AVAILABLE, not total
us_quantity = min(max_shares_by_risk, max_shares_by_size, max_shares_by_volume, max_shares_by_cash)

// For portfolio-wide percentage checks, convert everything to NGN:
total_portfolio_ngn = ngx_value_ngn + (us_value_usd * current_usd_ngn_rate)
```

---

## EXECUTION: TROVE BROWSER AGENT

### BrokerGateway Interface (Future API Migration)

```java
public interface BrokerGateway {
    boolean login();
    TradeOrder placeOrder(TradeOrder order);
    List<Position> readPortfolio();
    AccountBalance readBalance(Market market);
    BigDecimal getAvailableCash(Currency currency);
    List<TransactionRecord> readRecentTransactions(Market market, int count);
}

// Current implementation:
@Service
@ConditionalOnProperty(name = "trove.api.enabled", havingValue = "false", matchIfMissing = true)
public class TroveBrowserAgent implements BrokerGateway { ... }

// Future implementation (when API access granted):
@Service
@ConditionalOnProperty(name = "trove.api.enabled", havingValue = "true")
public class TroveApiClient implements BrokerGateway { ... }
```

### BrowserSessionLock — CRITICAL CONCURRENCY CONTROL

```
CLASS: BrowserSessionLock

PROBLEM: Multiple @Scheduled tasks may try to use Playwright simultaneously:
  - StopLossMonitor (every 15 min)
  - NewsScraperScheduler (every 15 min, but uses Playwright for PDF bulletin)
  - OrderManager (on signal)
  - ReconciliationScheduler (on startup + daily)
  - PortfolioTracker (for readPortfolio)

If two threads navigate the browser at the same time → race condition →
wrong data scraped, wrong forms filled, wrong screenshots captured.

SOLUTION: Single ReentrantLock. EVERY method on TroveBrowserAgent acquires this lock.

IMPLEMENTATION:
  private final ReentrantLock browserLock = new ReentrantLock(true);  // fair lock

  public TradeOrder placeOrder(TradeOrder order) {
      browserLock.lock();
      try {
          // ... all Playwright operations ...
      } finally {
          browserLock.unlock();
      }
  }

  // Same pattern for readPortfolio(), readBalance(), login(), etc.

ALTERNATIVE: Use a SingleThreadExecutor to serialize all browser tasks:
  private final ExecutorService browserExecutor = Executors.newSingleThreadExecutor();

  public CompletableFuture<TradeOrder> placeOrderAsync(TradeOrder order) {
      return CompletableFuture.supplyAsync(() -> placeOrderInternal(order), browserExecutor);
  }
```

### OTP/2FA Handler — CRITICAL FOR LOGIN

```
CLASS: OtpHandler

PROBLEM: Trove likely requires OTP verification on login from server environments.
Without handling this, the bot cannot log in at all.

FLOW:
  1. TroveBrowserAgent fills email + password, clicks submit
  2. If page shows OTP input field (detected by otp-input selector):
     a. Send WhatsApp message: "Trove requires OTP. Please reply with the code sent to your phone/email."
     b. Block and wait for WhatsApp reply (up to trove.otp.timeout-seconds = 120)
     c. Read reply message via WAHA webhook
     d. Enter OTP into the input field, click verify
     e. If OTP rejected → retry up to trove.otp.max-retries times
     f. If all retries exhausted → ALERT + abort login + set kill switch
  3. If no OTP field appears → login succeeded without OTP

IMPLEMENTATION:
  - Uses a CompletableFuture that completes when WhatsApp webhook receives a reply
  - WhatsAppWebhookController checks if an OTP request is pending
  - If pending, the reply text is treated as the OTP code
  - OtpHandler has an AtomicReference<CompletableFuture<String>> for pending OTP requests

CRITICAL: The OTP flow must work with BrowserSessionLock held — the browser
stays on the OTP page while waiting. No other task can use the browser.
```

### OrderRecoveryService — CRITICAL FOR PARTIAL FAILURES

```
CLASS: OrderRecoveryService

PROBLEM: What happens when Playwright clicks "Review" but then:
  - Internet drops?
  - Page times out?
  - Trove shows unexpected modal?
  - Browser crashes?
The order is in an unknown state. It might have gone through or not.

FLOW (on ANY Playwright failure mid-order):
  1. Catch the exception. Screenshot whatever the browser shows.
  2. Mark the order as UNCERTAIN in the database.
  3. Set the kill switch to HALT further trading.
  4. Send URGENT WhatsApp alert:
     "ORDER UNCERTAIN: BUY 100 ZENITHBANK @ N35.50
      Error: Page timeout after 'Review' click
      Screenshot: [attached]
      Trading is HALTED. Reply RESOLVED or FAILED to continue."
  5. Attempt recovery check (if browser is still responsive):
     a. Navigate to order history page
     b. Check if the order appears in recent orders
     c. If found → mark as FILLED or PENDING, clear kill switch
     d. If not found → mark as FAILED, clear kill switch
  6. If browser is NOT responsive → wait for manual resolution via WhatsApp

ORDER STATUS ENUM:
  PENDING, SUBMITTED, REVIEW_SCREEN, CONFIRMED, FILLED,
  PARTIALLY_FILLED, REJECTED, CANCELLED, FAILED,
  UNCERTAIN,        // ★ NEW — order outcome unknown
  MANUALLY_RESOLVED // ★ NEW — human confirmed final state
```

### TroveBrowserAgent Specification

```
CLASS: TroveBrowserAgent implements BrokerGateway

LIFECYCLE:
  - Single Playwright Browser bean (managed by PlaywrightConfig)
  - BrowserContext per session
  - ALL methods acquire BrowserSessionLock before touching browser
  - Session expires after trove.session-max-hours -> auto re-login

METHODS:
  login() -> boolean
    - Acquire BrowserSessionLock
    - Navigate to trove.base-url/login
    - Fill email, password, click submit
    - Detect OTP page -> delegate to OtpHandler if needed
    - Wait for dashboard
    - Screenshot: "login_success.png"

  placeOrder(TradeOrder order) -> TradeOrder
    - Acquire BrowserSessionLock
    - SAFETY: Kill switch. Market hours for order.market. Session valid.
    - If order.market == NGX:
      - Click NGX trade tab
      - Fill NGX order form (symbol, quantity, price, side)
      - Orders MUST be LIMIT for NGX
    - If order.market == US:
      - Click US trade tab
      - Fill US order form (symbol, amount/shares, order type, limit price)
      - Orders: LIMIT preferred, market OK for US liquid ETFs during DCA only
    - Screenshot every step: form_filled, review_screen, submitted
    - VERIFY review screen matches intended order
    - If mismatch -> ABORT + alert + screenshot
    - Click confirm
    - On ANY failure mid-order -> delegate to OrderRecoveryService
    - On US trade success -> capture Trove FX rate from confirmation page (TroveFxRateCapture)
    - Create settlement_ledger entry for sells (settles_on = trade_date + market.settlement_days)
    - Return updated order with screenshots + actual FX rate

  readPortfolio() -> List<Position>
    - Acquire BrowserSessionLock
    - Navigate to portfolio tab
    - Scrape NGX holdings section -> NGN positions
    - Scrape US holdings section -> USD positions
    - Return combined list with market + currency tags

  readBalance(Market market) -> AccountBalance
    - Acquire BrowserSessionLock
    - NGX: scrape naira balance
    - US: scrape dollar balance

  readRecentTransactions(Market market, int count) -> List<TransactionRecord>
    - Acquire BrowserSessionLock
    - Navigate to transactions tab
    - Filter by market
    - Extract transaction details including FX rate applied by Trove

SELECTORS: ALL from application.yml trove.selectors.*
  IMPORTANT: These are PLACEHOLDERS. Run playwright codegen to discover real ones.
```

---

## PORTFOLIO RECONCILIATION — CRITICAL STATE SYNC

### Why This Exists

The bot tracks positions in PostgreSQL. Trove's portal is the source of truth. These WILL drift:
- Bot crashes mid-trade (did it fill or not?)
- You manually buy/sell on the Trove mobile app
- A limit order fills while the bot is offline
- Dividend received but not recorded
- Corporate action (split, merger) changes share count
- Orders marked UNCERTAIN by OrderRecoveryService

Without reconciliation, risk calculations, position sizes, and cash balances silently diverge from reality.

### Reconciliation Specification

```
CLASS: PortfolioReconciler

TRIGGERS:
  1. Every application startup (reconciliation.run-on-startup = true)
  2. Daily at 09:00 WAT (before any trading begins)
  3. After any UNCERTAIN order is resolved
  4. Manually via REST endpoint: POST /api/reconcile

FLOW:
  1. Acquire BrowserSessionLock (reconciliation uses Playwright)
  2. Scrape Trove portfolio -> List<TrovePosition> (symbol, shares, market, currency, current_price)
  3. Read internal DB positions -> List<Position> (same fields)
  4. COMPARE:
     For each Trove position:
       - If exists in DB with SAME share count -> OK
       - If exists in DB with DIFFERENT share count -> MISMATCH
       - If NOT in DB -> MISSING_INTERNAL (Trove has it, we don't)
     For each DB position:
       - If NOT in Trove -> GHOST_POSITION (we think we hold it, but we don't)
  5. CASH RECONCILIATION (CashReconciler):
     - Scrape Trove NGN balance, USD balance
     - Compare to internal available_cash + settling_cash
     - Allow cash-tolerance-pct (1%) for rounding
  6. FX RATE CHECK (FxRateReconciler):
     - Scrape Trove's current displayed FX rate
     - Compare to our stored market rate
     - Log the spread for cost tracking
  7. REPORT:
     - Generate ReconciliationReport with all matches, mismatches, ghosts, missing
     - If ANY position mismatch exists AND mismatch-action == ALERT_AND_HALT:
       -> Set kill switch
       -> Send WhatsApp alert with full mismatch details
       -> Trading is halted until manual resolution
     - If all positions match -> log success, continue trading

CLASS: CashReconciler
  - After every sell order, creates settlement_ledger entry
  - Periodically checks: has settlement date passed? -> move settling to available
  - On reconciliation: compare our available + settling totals to Trove's displayed balances
```

---

## BACKTESTING ENGINE — PROVE BEFORE LIVE

### Why This Exists

Every strategy must demonstrate positive expected value on historical data before receiving real money. Without this, the system is a random number generator with transaction costs.

### Backtest Specification

```
CLASS: BacktestRunner

PURPOSE: Replay historical OHLCV data through strategies, simulate order fills,
         compute performance metrics.

INPUT:
  - Strategy to test (or ALL strategies)
  - Market (NGX, US, or BOTH)
  - Date range (start_date, end_date)
  - Starting capital (NGN and/or USD)
  - Slippage assumption (default 0.1%)
  - Commission rate (0.5% NGX, 1.5% US)

FLOW:
  1. Load historical OHLCV from database for date range
  2. For each trading day, in chronological order:
     a. Feed day's data to strategy -> get signals
     b. Feed signals to RiskManager -> get approved orders
     c. Feed approved orders to SimulatedOrderExecutor -> simulate fills
     d. Update simulated portfolio state
     e. Record daily portfolio value, cash, positions
  3. After all days processed, compute metrics via PerformanceAnalyzer

CLASS: SimulatedOrderExecutor
  - Fills at open of next candle + slippage (conservative)
  - Deducts commission
  - Handles partial fills for illiquid NGX stocks (volume check)
  - Tracks settlement: sell proceeds not available for settlement_days

CLASS: PerformanceAnalyzer
  OUTPUT:
  - Total return (%)
  - Annualized return (%)
  - Sharpe ratio (risk-free rate = 12% for NGN, 5% for USD)
  - Max drawdown (% and duration in days)
  - Win rate (% of trades profitable)
  - Profit factor (gross profit / gross loss)
  - Average win vs average loss
  - Number of trades
  - Average holding period (days)
  - Per-strategy breakdown

REST API:
  POST /api/backtest          -> run a backtest
  GET  /api/backtest/{id}     -> get backtest results
  GET  /api/backtest/history  -> list past backtest runs

MINIMUM THRESHOLDS TO GO LIVE (per strategy):
  - Sharpe ratio > 0.5
  - Profit factor > 1.3
  - Max drawdown < 20%
  - Win rate > 40%
  - Tested on minimum 6 months of data
```

---

## DATABASE SCHEMA — NEW/MODIFIED TABLES

### V12: Add Market + Currency + Pool to Existing Tables

```sql
-- Positions: add market, currency, FX tracking
ALTER TABLE positions ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'NGX';
ALTER TABLE positions ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
ALTER TABLE positions ADD COLUMN fx_rate_at_entry DECIMAL(12,4);
ALTER TABLE positions ADD COLUMN broker_fx_rate_at_entry DECIMAL(12,4);  -- ★ actual Trove rate
ALTER TABLE positions ADD COLUMN pool VARCHAR(10) NOT NULL DEFAULT 'SATELLITE';
ALTER TABLE positions ADD COLUMN target_weight_pct DECIMAL(6,2);
CREATE INDEX idx_positions_market ON positions(market, pool, is_open);

-- Trade orders: add market, currency, FX, recovery fields
ALTER TABLE trade_orders ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'NGX';
ALTER TABLE trade_orders ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
ALTER TABLE trade_orders ADD COLUMN fx_rate DECIMAL(12,4);
ALTER TABLE trade_orders ADD COLUMN broker_fx_rate DECIMAL(12,4);        -- ★ actual Trove rate
ALTER TABLE trade_orders ADD COLUMN pool VARCHAR(10) NOT NULL DEFAULT 'SATELLITE';
ALTER TABLE trade_orders ADD COLUMN recovery_status VARCHAR(20);          -- ★ UNCERTAIN tracking
ALTER TABLE trade_orders ADD COLUMN recovery_checked_at TIMESTAMP;
ALTER TABLE trade_orders ADD COLUMN recovery_screenshot_path TEXT;

-- Portfolio snapshots: multi-market breakdown
ALTER TABLE portfolio_snapshots ADD COLUMN ngx_value_ngn DECIMAL(14,2);
ALTER TABLE portfolio_snapshots ADD COLUMN us_value_usd DECIMAL(14,2);
ALTER TABLE portfolio_snapshots ADD COLUMN us_value_ngn DECIMAL(14,2);
ALTER TABLE portfolio_snapshots ADD COLUMN usd_ngn_rate DECIMAL(12,4);
ALTER TABLE portfolio_snapshots ADD COLUMN broker_usd_ngn_rate DECIMAL(12,4);
ALTER TABLE portfolio_snapshots ADD COLUMN core_value DECIMAL(14,2);
ALTER TABLE portfolio_snapshots ADD COLUMN satellite_value DECIMAL(14,2);
ALTER TABLE portfolio_snapshots ADD COLUMN core_pct DECIMAL(6,2);
ALTER TABLE portfolio_snapshots ADD COLUMN satellite_pct DECIMAL(6,2);
ALTER TABLE portfolio_snapshots ADD COLUMN ngx_pct DECIMAL(6,2);
ALTER TABLE portfolio_snapshots ADD COLUMN us_pct DECIMAL(6,2);
ALTER TABLE portfolio_snapshots ADD COLUMN total_dividends_ytd DECIMAL(14,2);

-- Core holdings: add market, currency
ALTER TABLE core_holdings ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'NGX';
ALTER TABLE core_holdings ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
DROP INDEX IF EXISTS core_holdings_symbol_key;
ALTER TABLE core_holdings ADD CONSTRAINT uk_core_holdings UNIQUE(symbol, market);

-- DCA plans: add market, currency, budget in local currency
ALTER TABLE dca_plans ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'NGX';
ALTER TABLE dca_plans ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
ALTER TABLE dca_plans ADD COLUMN monthly_budget_local DECIMAL(12,2);

-- Dividend events: add market, withholding tax
ALTER TABLE dividend_events ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'NGX';
ALTER TABLE dividend_events ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
ALTER TABLE dividend_events ADD COLUMN withholding_tax_pct DECIMAL(6,2) DEFAULT 0;
ALTER TABLE dividend_events ADD COLUMN net_amount_received DECIMAL(14,2);
```

### V13: FX Rates

```sql
CREATE TABLE fx_rates (
    id BIGSERIAL PRIMARY KEY,
    pair VARCHAR(10) NOT NULL,          -- 'USD_NGN'
    rate_date DATE NOT NULL,
    rate DECIMAL(12,4) NOT NULL,
    source VARCHAR(30),                 -- 'EODHD', 'CBN', 'TROVE'
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(pair, rate_date, source)
);

CREATE INDEX idx_fx_rate_pair_date ON fx_rates(pair, rate_date DESC);
```

### V14: News Articles

```sql
CREATE TABLE news_articles (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    url TEXT,
    published_at TIMESTAMP,
    scraped_at TIMESTAMP DEFAULT NOW(),
    market VARCHAR(5),
    matched_symbols TEXT[],
    snippet TEXT,
    is_processed BOOLEAN DEFAULT FALSE,
    UNIQUE(url)
);

CREATE INDEX idx_news_source_date ON news_articles(source, published_at DESC);
CREATE INDEX idx_news_unprocessed ON news_articles(is_processed) WHERE is_processed = FALSE;
```

### V15: News Events (Classified)

```sql
CREATE TABLE news_events (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT REFERENCES news_articles(id),
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    impact_direction VARCHAR(10),
    confidence DECIMAL(4,2),
    signal_modifier_pct DECIMAL(6,2),
    key_figure TEXT,
    is_actionable BOOLEAN DEFAULT FALSE,
    acted_on BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_news_event_symbol ON news_events(symbol, created_at DESC);
CREATE INDEX idx_news_event_actionable ON news_events(is_actionable) WHERE acted_on = FALSE;
```

### V16: Settlement Ledger ★ NEW

```sql
CREATE TABLE settlement_ledger (
    id BIGSERIAL PRIMARY KEY,
    trade_order_id BIGINT REFERENCES trade_orders(id),
    market VARCHAR(5) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,       -- Positive = cash incoming (sell), Negative = cash outgoing (buy)
    trade_date DATE NOT NULL,
    settles_on DATE NOT NULL,            -- trade_date + settlement_days
    is_settled BOOLEAN DEFAULT FALSE,
    settled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_settlement_unsettled ON settlement_ledger(market, currency, is_settled)
    WHERE is_settled = FALSE;
CREATE INDEX idx_settlement_settles_on ON settlement_ledger(settles_on)
    WHERE is_settled = FALSE;
```

### V17: Reconciliation Log ★ NEW

```sql
CREATE TABLE reconciliation_log (
    id BIGSERIAL PRIMARY KEY,
    reconciled_at TIMESTAMP DEFAULT NOW(),
    trigger_reason VARCHAR(30) NOT NULL,  -- 'STARTUP', 'DAILY', 'MANUAL', 'POST_UNCERTAIN'
    status VARCHAR(20) NOT NULL,          -- 'MATCHED', 'MISMATCH', 'ERROR'
    positions_matched INTEGER DEFAULT 0,
    positions_mismatched INTEGER DEFAULT 0,
    ghost_positions INTEGER DEFAULT 0,     -- In DB but not on Trove
    missing_positions INTEGER DEFAULT 0,   -- On Trove but not in DB
    ngn_cash_db DECIMAL(14,2),
    ngn_cash_broker DECIMAL(14,2),
    usd_cash_db DECIMAL(14,2),
    usd_cash_broker DECIMAL(14,2),
    broker_fx_rate DECIMAL(12,4),
    market_fx_rate DECIMAL(12,4),
    fx_spread_pct DECIMAL(6,2),
    details_json JSONB,                    -- Full mismatch details
    action_taken VARCHAR(30)               -- 'NONE', 'ALERT_SENT', 'KILL_SWITCH_SET'
);
```

### V18: Add Order Recovery Columns ★ NEW

```sql
-- Already covered in V12's trade_orders additions, but if V12 was created before
-- the recovery feature, add these columns:
-- ALTER TABLE trade_orders ADD COLUMN recovery_status VARCHAR(20);
-- ALTER TABLE trade_orders ADD COLUMN recovery_checked_at TIMESTAMP;
-- ALTER TABLE trade_orders ADD COLUMN recovery_screenshot_path TEXT;

-- Add UNCERTAIN to any check constraints on order status
-- Add order_status_history for audit trail:
CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    trade_order_id BIGINT REFERENCES trade_orders(id),
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    changed_by VARCHAR(20) DEFAULT 'SYSTEM', -- 'SYSTEM', 'RECOVERY', 'MANUAL', 'RECONCILIATION'
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_order_status_history ON order_status_history(trade_order_id, created_at DESC);
```

### V19: AI Analysis Table ★ NEW

```sql
CREATE TABLE ai_analysis (
    id BIGSERIAL PRIMARY KEY,
    news_article_id BIGINT REFERENCES news_articles(id),
    analysis_type VARCHAR(30) NOT NULL,    -- 'ARTICLE', 'EARNINGS', 'INSIDER_PATTERN', 'DAILY_SYNTHESIS'
    model_used VARCHAR(50) NOT NULL,       -- 'claude-haiku-4-5-20251001', 'claude-sonnet-4-5-20250929'
    symbol VARCHAR(20),
    market VARCHAR(5),                     -- 'NGX', 'US'
    sentiment DECIMAL(4,3),                -- -1.000 to +1.000
    confidence DECIMAL(4,3),               -- 0.000 to 1.000
    signal_modifier_pct DECIMAL(6,3),      -- -30.000 to +30.000
    key_factors TEXT,                       -- JSON array of strings
    nuance TEXT,                            -- One-sentence insight regex would miss
    contradicts_rule_based BOOLEAN DEFAULT FALSE,
    rule_based_correct BOOLEAN DEFAULT TRUE,
    full_response TEXT,                     -- Raw JSON response for audit
    input_tokens INTEGER,
    output_tokens INTEGER,
    cost_usd DECIMAL(8,6),                 -- Cost of this specific API call
    processing_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ai_analysis_article ON ai_analysis(news_article_id);
CREATE INDEX idx_ai_analysis_symbol ON ai_analysis(symbol, created_at DESC);
CREATE INDEX idx_ai_analysis_type ON ai_analysis(analysis_type, created_at DESC);

-- Daily cost tracking (materialized via application, not DB view)
CREATE TABLE ai_cost_ledger (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    model VARCHAR(50) NOT NULL,
    call_count INTEGER DEFAULT 0,
    total_input_tokens BIGINT DEFAULT 0,
    total_output_tokens BIGINT DEFAULT 0,
    total_cost_usd DECIMAL(8,4) DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(date, model)
);
```

### V20: Stock Discovery Tables ★ NEW

```sql
CREATE TABLE discovered_stocks (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,            -- 'NGX', 'US'
    status VARCHAR(20) NOT NULL,           -- 'CANDIDATE', 'OBSERVATION', 'PROMOTED', 'DEMOTED'
    discovery_source VARCHAR(30) NOT NULL, -- 'SCREENER', 'NEWS', 'INSIDER_BUYING'
    discovered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    observation_started_at TIMESTAMP,
    promoted_at TIMESTAMP,
    demoted_at TIMESTAMP,
    demoted_reason TEXT,
    cooldown_until TIMESTAMP,              -- Cannot re-discover before this date
    fundamental_score INTEGER,
    ai_assessment_score INTEGER,           -- Haiku evaluation (0-100)
    ai_assessment_reasoning TEXT,
    market_cap DECIMAL(18,2),
    avg_daily_volume BIGINT,
    sector VARCHAR(100),
    industry VARCHAR(100),
    last_signal_at TIMESTAMP,              -- Tracks when last strategy signal was generated
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(symbol, market)
);

CREATE INDEX idx_discovered_status ON discovered_stocks(status);
CREATE INDEX idx_discovered_market ON discovered_stocks(market, status);

CREATE TABLE discovery_events (
    id BIGSERIAL PRIMARY KEY,
    discovered_stock_id BIGINT REFERENCES discovered_stocks(id),
    event_type VARCHAR(30) NOT NULL,       -- 'DISCOVERED', 'OBSERVATION_START', 'PROMOTED', 'DEMOTED', 'REDISCOVERED'
    reason TEXT,
    metadata TEXT,                          -- JSON: screener filters, news article ID, AI score, etc.
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_discovery_events ON discovery_events(discovered_stock_id, created_at DESC);
```

---

## SCHEDULING SUMMARY (MULTI-TIMEZONE)

All cron uses `zone` parameter. NGX tasks use Africa/Lagos, US tasks use America/New_York.

| Task | Cron | Zone | Module |
|------|------|------|--------|
| **Reconciliation on startup** | ApplicationReadyEvent | Lagos | ReconciliationScheduler |
| Daily reconciliation | `0 0 9 * * MON-FRI` | Lagos | ReconciliationScheduler |
| Settlement ledger settle check | `0 0 8 * * MON-FRI` | Lagos | SettlementCashTracker |
| NGX pre-market news scan | `0 0 7 * * MON-FRI` | Lagos | NewsScraperScheduler |
| NGX data pull (EODHD) | `0 0 15 * * MON-FRI` | Lagos | DataCollectionScheduler |
| NGX signal generation | `0 15 15 * * MON-FRI` | Lagos | SignalGenerationScheduler |
| NGX ETF NAV scrape | `0 0 16 * * MON-FRI` | Lagos | DataCollectionScheduler |
| NGX Daily Bulletin parse | `0 0 17 * * MON-FRI` | Lagos | NgxBulletinScheduler |
| NGX stop-loss monitor | `0 */15 10-14 * * MON-FRI` | Lagos | StopLossMonitor |
| US pre-market news scan | `0 0 14 * * MON-FRI` | Lagos | NewsScraperScheduler |
| US data pull (EODHD) | `0 30 16 * * MON-FRI` | New_York | DataCollectionScheduler |
| US signal generation | `0 45 16 * * MON-FRI` | New_York | SignalGenerationScheduler |
| US stop-loss monitor | `0 */15 9-16 * * MON-FRI` | New_York | StopLossMonitor |
| FX rate update | `0 0 9,15,21 * * MON-FRI` | Lagos | FxRateClient |
| News scrape (continuous) | `0 */15 7-22 * * MON-FRI` | Lagos | NewsScraperScheduler |
| Daily summary | `0 30 22 * * MON-FRI` | Lagos | PerformanceReporter |
| Weekly report | `0 0 23 * * FRI` | Lagos | PerformanceReporter |
| NGX DCA (monthly) | `0 15 10 5 * *` | Lagos | DcaScheduler |
| US DCA (monthly) | `0 00 10 10 * *` | New_York | DcaScheduler |
| Weekly dividend scan | `0 0 9 * * MON` | Lagos | DividendCheckScheduler |
| Weekly fundamental refresh | `0 0 20 * * SUN` | Lagos | FundamentalUpdateScheduler |
| Quarterly rebalance | `0 0 15 1 1,4,7,10 *` | Lagos | RebalanceScheduler |
| Monthly portfolio report | `0 0 9 1 * *` | Lagos | PerformanceReporter |
| Portfolio snapshot | `0 45 22 * * MON-FRI` | Lagos | PortfolioTracker |
| **AI article analysis** | `0 */15 7-22 * * MON-FRI` | Lagos | AiAnalysisScheduler |
| **AI cross-article synthesis** | `0 0 21 * * MON-FRI` | Lagos | AiAnalysisScheduler |
| **AI cost budget check** | `0 0 0 * * *` | Lagos | AiCostTracker |
| **NGX stock discovery scan** | `0 0 18 * * SUN` | Lagos | DiscoveryScheduler |
| **US stock discovery scan** | `0 0 20 * * SUN` | Lagos | DiscoveryScheduler |
| **Discovery observation check** | `0 0 8 * * MON-FRI` | Lagos | DiscoveryScheduler |
| **Discovery demotion check** | `0 0 7 * * MON-FRI` | Lagos | DiscoveryScheduler |

---

## DEVELOPMENT PHASES (16 PHASES)

### Phase 1: Project Scaffold + Database
```
"Initialize Spring Boot project with Maven. Create pom.xml with all dependencies.
Create ALL ConfigurationProperties classes including TroveProperties, NewsProperties,
AiProperties, ReconciliationProperties, BacktestProperties, DiscoveryProperties.
Create ALL Flyway migrations V1-V20 (including settlement_ledger, reconciliation_log,
order_status_history, ai_analysis, discovered_stocks). Create all JPA entities with market, currency, pool fields.
Create Market, Currency, PortfolioPool enums. Create application.yml with full config.
Create Docker Compose. Verify compilation and schema creation."
```

### Phase 2: Data Pipeline (Multi-Market)
```
"Build data pipeline for BOTH NGX and US markets. EodhdApiClient handles both
.XNSA (NGX) and .US ticker formats. Create FxRateClient for USD/NGN rates.
Create NgxWebScraper, EtfNavScraper. Create UsEarningsCalendarClient.
Create DataCollectionScheduler with market-aware cron jobs.
Unit tests for EODHD client with both NGX and US tickers."
```

### Phase 3: FX Module
```
"Build the fx package. Create FxRateService (current + historical rates),
FxConverter (NGN<->USD conversion), CurrencyExposureTracker.
Create TroveFxRateCapture (stub for now — implemented fully in Phase 11).
Create Money value object (amount + currency). All monetary operations must
use Money objects that are currency-aware. Test FX conversion accuracy."
```

### Phase 4: Technical Indicators + Fundamental Scorer
```
"Build signal engine: RSI, MACD, SMA(20), SMA(200), EMA, ATR, volume analyzer.
Create FundamentalScorer (0-100 composite). Create ValueScreener.
These work identically for NGX and US stocks — they operate on OHLCV data regardless of market."
```

### Phase 5: News Intelligence Module
```
"Build the entire news package. Create scrapers: NairametricsScraper, BusinessDayScraper,
ReutersRssScraper, SeekingAlphaScraper, CbnPressScraper, NgxBulletinParser.
Use Jsoup for HTML/RSS, Apache PDFBox for NGX bulletin PDF parsing.
Create NewsEventClassifier with regex patterns for all 17 EventType values.
Create EventImpactRules mapping event types to signal modifiers.
Create InsiderTradeDetector for Form 29 parsing.
Create NewsImpactScorer. Create NewsScraperScheduler.
Test classifier against sample headlines. Test insider trade detection."
```

### Phase 5b: AI Intelligence Layer (Claude API) ★ NEW
```
"Build the ai package on top of the news module.

1. AiProperties @ConfigurationProperties for all ai.* config.
2. ClaudeApiClient — WebClient wrapper for Anthropic Messages API.
   POST to /v1/messages with model, system prompt, user prompt.
   Parse response JSON. Extract token counts for cost tracking.
   Retry on 5xx/429 with exponential backoff (max 2 retries).

3. AiCostTracker — tracks per-call cost using Anthropic's published pricing.
   Haiku: $0.25/1M input, $1.25/1M output.
   Sonnet: $3/1M input, $15/1M output.
   Accumulates daily + monthly totals in ai_cost_ledger table.
   Before EVERY API call: check budget. If exceeded → return Optional.empty().

4. AiFallbackHandler — returns empty Optional on any failure.
   API error, budget exceeded, malformed JSON, timeout → all silently degrade.
   Logs warning. Tracks fallback count. Alerts if fallback rate > 50%.

5. AiNewsAnalyzer — sends article headline + truncated body to Haiku.
   Parses JSON response into AiAnalysis entity.
   If event is in deep-analysis-triggers list OR Haiku confidence < 50%:
   re-analyze with Sonnet for deeper assessment.

6. AiEarningsAnalyzer — Sonnet-only for earnings events.
   Adds forward guidance, management tone, revenue quality assessment.

7. AiCrossArticleSynthesizer — daily 21:00 WAT batch.
   For each stock with 2+ articles today: concatenate, send to Haiku.
   Outputs consensus sentiment and emerging narrative.

8. AiInsiderTradeInterpreter — Haiku analysis of insider trade patterns.
   Triggered after InsiderTradeDetector runs.

9. AiAnalysisScheduler — runs every 15 min, picks up unanalyzed articles.
   Processes in batches of 10 with 5s delay between API calls.

10. Update NewsImpactScorer to blend rule-based (50%) + AI (50%) for the
    10% news component. Total AI influence on signal = 5% of total weight.

11. Flyway V19 migration for ai_analysis + ai_cost_ledger tables.
12. AiAnalysis entity + repository.

Test: mock ClaudeApiClient, verify fallback on API failure,
verify cost tracking, verify blended score calculation,
verify budget enforcement stops calls when exceeded."
```

### Phase 5c: Stock Discovery Module ★ NEW
```
"Build the discovery package for dynamic watchlist expansion.

1. DiscoveryProperties @ConfigurationProperties for all discovery.* config.
2. EodhdScreenerClient — wraps EODHD Screener API. Builds filter JSON for
   NGX (exchange=XNSA) and US (exchange=US). Parses response into candidates.
3. DiscoveredStock entity + DiscoveryEvent entity + repositories.
4. WatchlistManager — merges SEED (from YAML) + PROMOTED (from discovery).
   Exposes getActiveWatchlist() used by DataCollectionScheduler and all strategies.
   Enforces max_active_watchlist_size and max_observation_slots limits.
5. CandidateEvaluator — 3-stage filter: basic (market cap, volume, EPS),
   fundamental (P/E, revenue growth, debt), optional AI assessment (Haiku).
   Stocks passing all stages → OBSERVATION status.
6. NewsDiscoveryListener — hooks into NewsEventClassifier output.
   When article mentions stock NOT in watchlist → create CANDIDATE.
   If HIGH_IMPACT event → fast-track to OBSERVATION.
7. PromotionPolicy — checks observation period met, buy signal generated,
   volume consistent, no red flag events, fundamental score > 50.
   Promotes OBSERVATION → PROMOTED. WhatsApp notification on promotion.
8. DemotionPolicy — checks for volume drop, fundamental decline, no signals
   in 60 days, regulatory action. Demotes PROMOTED → DEMOTED.
   Triggers position close for demoted stocks. 90-day cooldown.
9. DiscoveryScheduler — weekly screener scans (Sunday),
   daily observation checks, daily demotion checks.
10. Flyway V20 migration for discovered_stocks + discovery_events tables.
11. Update DataCollectionScheduler to pull OHLCV for OBSERVATION + PROMOTED stocks.

Test: mock EODHD screener, verify candidate filtering pipeline,
verify promotion criteria, verify demotion closes positions,
verify SEED stocks are never demoted, verify max size limits enforced."
```

### Phase 6: All 11 Strategies + Signal Scorer
```
"Implement all 11 trading strategies. Each implements Strategy interface with getPool()
and getMarket() methods. Create the 2 new US strategies: UsEarningsMomentumStrategy and
UsEtfRotationStrategy. Create CompositeSignalScorer with 4-component weighting:
tech 40%, fundamental 30%, NAV 20%, news 10% (news component blends rule-based 50% +
AI 50%, falling back to 100% rule-based if AI unavailable).
Create SignalGenerationScheduler that runs at different times per market."
```

### Phase 7: Risk Management (Pool + Market + Currency + Settlement Aware)
```
"Build pool-aware AND market-aware risk management. RiskManager checks pool AND market.
Create CurrencyExposureChecker (max 70% in any currency).
Create GeographicExposureChecker (max 75% in any market).
Create SettlementCashTracker — maintains per-market, per-currency ledger of settling
vs available cash. PositionSizer queries SettlementCashTracker.getAvailableCash()
and NEVER uses total cash. StopLossMonitor runs during BOTH market hours.
CircuitBreaker only applies to satellite. Cross-market circuit breaker for global events.
Test all combinations: core+NGX, core+US, satellite+NGX, satellite+US.
Test that settlement tracking correctly prevents spending unsettled funds."
```

### Phase 8: Long-Term Portfolio Engine (Multi-Currency)
```
"Build longterm package with multi-currency support. DcaExecutor handles separate
NGN and USD budgets on different days. DividendTracker knows about 30% US withholding tax.
PortfolioRebalancer works across markets (may rebalance from overweight NGX to underweight US).
CoreHoldings tracked per market with currency. All 4 schedulers created."
```

### Phase 9: Notifications
```
"Build WhatsApp + Telegram notifications. MessageFormatter is currency-aware (N vs $).
Add all notification events: news alerts, insider trade alerts,
cross-market circuit breaker, US earnings alerts, monthly multi-market report,
OTP request messages, order recovery URGENT alerts, reconciliation mismatch alerts,
settlement transition confirmations.
Approval flow: satellite trades (5-min), rebalance (60-min), DCA (auto-approved)."
```

### Phase 10: Backtesting Engine ★ NEW
```
"Build the complete backtest package. BacktestRunner replays historical OHLCV data
through strategies. SimulatedOrderExecutor fills at next candle open + slippage,
deducts commissions, respects settlement rules.
PerformanceAnalyzer computes: Sharpe ratio, max drawdown, win rate, profit factor,
average holding period, per-strategy breakdown.
BacktestReport entity persisted to DB. BacktestController REST API.
Must support BOTH NGX and US backtesting with respective commission rates.
Run backtest on at least 6 months of data for each strategy before Phase 12."
```

### Phase 11: Playwright Execution (Trove) + Reconciliation + Recovery
```
"Build execution layer — the most critical phase.

1. BrowserSessionLock: ReentrantLock that serializes ALL Playwright operations.
   Every method on TroveBrowserAgent acquires this lock.

2. OtpHandler: Detects OTP page, sends WhatsApp prompt, waits for reply via
   CompletableFuture, enters OTP code, retries up to max-retries.

3. TroveBrowserAgent implementing BrokerGateway interface.
   Single login, single agent for BOTH NGX and US orders.
   NGX: navigate to NGX trade tab, fill form, LIMIT orders only.
   US: navigate to US trade tab, fill form, LIMIT preferred.
   Captures Trove's actual FX rate from US transaction confirmation page.
   Creates settlement_ledger entries for every sell order.

4. OrderRecoveryService: On any Playwright failure mid-order, marks order UNCERTAIN,
   sets kill switch, sends urgent WhatsApp alert with screenshot, attempts to check
   order history to determine if order went through.

5. PortfolioReconciler + CashReconciler + FxRateReconciler:
   Runs on startup + daily. Scrapes Trove's actual portfolio and cash.
   Compares to internal DB state. On mismatch: ALERT + HALT trading.
   FxRateReconciler captures Trove's displayed FX rate vs market rate.

6. TroveApiClient STUB with @ConditionalOnProperty for future migration.

7. Create OrderRouter that dispatches by market. Screenshot every step.
   Verify review screen. Kill switch checks throughout."
```

### Phase 12: Dashboard + Monitoring
```
"REST API dashboard with multi-market, multi-currency views:
- GET /api/portfolio -> total in NGN, breakdown by market + pool
- GET /api/portfolio/ngx, /api/portfolio/us -> per-market views
- GET /api/portfolio/core, /api/portfolio/satellite -> per-pool
- GET /api/fx -> current USD/NGN rate (market + Trove), currency exposure, FX spread cost
- GET /api/news -> recent news events, impact scores
- GET /api/signals -> today's signals per market
- GET /api/dividends -> with withholding tax tracking
- GET /api/settlement -> settling vs available cash per market/currency
- GET /api/reconciliation -> latest reconciliation report
- GET /api/ai/cost -> daily/monthly AI spend, budget remaining
- GET /api/ai/analysis/{symbol} -> recent AI analyses for a stock
- GET /api/ai/fallback-rate -> % of articles where AI was unavailable
- GET /api/discovery/active -> current SEED + PROMOTED watchlist
- GET /api/discovery/candidates -> stocks in CANDIDATE and OBSERVATION status
- GET /api/discovery/history -> promotion/demotion audit trail
- GET /api/performance -> per-market, per-pool, combined (NGN base)
- POST /api/reconcile -> manual reconciliation trigger
- POST /api/backtest -> trigger backtest run
PerformanceReporter: separate metrics per market + pool, combined monthly report.
Include Trove FX spread cost as a line item in all performance reports."
```

### Phase 13: Integration Testing
```
"End-to-end integration tests (paper trading mode).
- Test full signal -> risk -> execution flow for both NGX and US
- Test settlement tracking prevents spending unsettled cash
- Test reconciliation detects manually added positions
- Test OTP flow with mocked WhatsApp
- Test order recovery on simulated Playwright failure
- Test news event classification -> signal modification -> trade approval
- Test DCA execution across both markets
- Test cross-market circuit breaker
- Test FX rate dual tracking (market vs Trove)
- Test AI fallback: when Claude API is down, signals still generate normally
- Test AI cost tracking: budget exceeded → graceful degradation
- Test AI blended scoring: rule-based + AI produce correct composite
- Test discovery pipeline: screener → candidate → observation → promotion
- Test discovery demotion: volume drop → position close → cooldown
- Test WatchlistManager: SEED stocks never demoted, size limits enforced
- Verify kill switch halts BOTH markets"
```

### Phase 14: Docker + Deployment
```
"Production Dockerfile with Playwright/Chromium. Docker Compose with bot + postgres + waha.
.env.example with all variables including TROVE_USERNAME, TROVE_PASSWORD, ANTHROPIC_API_KEY.
application-prod.yml. Test full stack.
Document Hetzner VPS deployment. Verify 13-hour operational window (9AM-10PM WAT).
Verify reconciliation runs on container restart.
Ensure settlement_ledger persists across restarts.
Ensure all screenshots stored in persistent volume."
```

---

## CODING CONVENTIONS

- Java 21 features: records, sealed interfaces, pattern matching
- Lombok everywhere: `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`
- **All monetary values: `BigDecimal` with `Currency` context — use `Money` value object**
- All dates: `LocalDate` for trade dates, `ZonedDateTime` with market-specific timezone
- Percentages stored as decimal (0.02 = 2%)
- Constructor injection ONLY
- **Every entity with financial data MUST have `market` and `currency` fields**
- **Every TradeSignal, Position, TradeOrder MUST have `market`, `currency`, `pool` fields**
- **Every US trade MUST record both `fx_rate` (market) and `broker_fx_rate` (actual)**
- All Trove selectors from application.yml — NEVER hardcoded
- **ALL Playwright operations MUST acquire BrowserSessionLock**
- SLF4J structured logging with market context: `log.info("[{}] Signal: {} {} @ {}", market, side, symbol, price)`
- **All ClaudeApiClient calls wrapped in AiFallbackHandler — never throw on API failure**
- **All AI JSON parsing uses try-catch with fallback to rule-based score**
- **AiCostTracker.checkBudget() called before EVERY ClaudeApiClient.analyze() call**
- **WatchlistManager.getActiveWatchlist() is the ONLY source of tradeable stocks** — strategies, data pipeline, and signal generator all use this method
- **SEED stocks (from YAML) must NEVER be demoted** — guard in DemotionPolicy
- Tests: JUnit 5 + Mockito + AssertJ

---

## IMPORTANT WARNINGS

1. **NEVER use market orders on NGX.** LIMIT only. (US liquid ETFs: market OK during DCA only.)
2. **NEVER trade outside market hours** for the respective market. Hardcoded per-market check.
3. **NEVER skip review screen verification.**
4. **NEVER execute without risk checks passing.**
5. **Kill switch halts BOTH markets instantly.**
6. **All monetary math uses BigDecimal + Currency context.**
7. **Trove selectors are PLACEHOLDERS.** Run `playwright codegen https://app.trovefinance.com`.
8. **Don't go live without backtesting.** Prove positive EV — Sharpe > 0.5, profit factor > 1.3, max drawdown < 20%.
9. **NEVER apply satellite circuit breakers to core positions.**
10. **NEVER sell a core dividend stock on price drop alone.** Only on fundamental deterioration.
11. **Pool allocation + currency exposure + geographic exposure checks are GLOBAL.** Check all three before ANY trade.
12. **DCA is non-negotiable.** Runs monthly in BOTH markets, even during crashes.
13. **US dividends are taxed 30%.** Always use NET yield in calculations, never gross.
14. **News impact is CAPPED at 10% of signal weight.** News informs, it does not drive trades.
15. **FX rate at entry is recorded on EVERY US position — BOTH market rate and Trove rate.**
16. **When Trove API access is pursued:** flip `trove.api.enabled=true` and TroveApiClient activates via `@ConditionalOnProperty`. Zero architecture changes needed.
17. **NEVER spend unsettled cash.** PositionSizer MUST call SettlementCashTracker.getAvailableCash(), never total cash.
18. **Reconciliation runs on EVERY startup and EVERY morning before trading.** If mismatch detected, trading halts.
19. **ALL Playwright methods MUST acquire BrowserSessionLock.** No concurrent browser access, ever.
20. **OTP handler is BLOCKING.** While waiting for OTP, no other browser operation can proceed. This is by design.
21. **On ANY mid-order failure, mark order UNCERTAIN, set kill switch, alert immediately.** Never assume the order failed — it might have gone through.
22. **Track Trove's actual FX rate on every US trade.** The spread is a real cost that erodes returns.
23. **AI is enrichment, NOT dependency.** The bot MUST function identically without Claude API. AiFallbackHandler ensures silent degradation on any failure.
24. **AI controls only 5% of total signal weight** (50% of the 10% news component). Even catastrophically wrong AI output cannot cause a trade by itself.
25. **NEVER call Claude API in the trade execution path.** AI analysis is async, batched, and runs AFTER articles are scraped — never blocking order placement.
26. **AiCostTracker checks budget BEFORE every API call.** If daily or monthly budget exceeded, AI calls stop immediately. No exceptions, no overrides.
27. **Parse AI JSON responses defensively.** Malformed JSON, missing fields, out-of-range values → fall back to rule-based. Never trust raw LLM output without validation.
28. **Truncate article body to max_input_tokens_per_article before sending.** Controls cost and prevents token blowup on long articles.
29. **SEED stocks are PERMANENT.** The YAML watchlist is never modified by the discovery module. Discovery only ADDS stocks, never removes originals.
30. **Discovered stocks must complete observation period before any strategy can trade them.** No shortcuts — minimum 7 days NGX, 5 days US of OHLCV data collection.
31. **WatchlistManager enforces size limits.** max_active_watchlist_size (60) prevents EODHD API cost blowup and keeps the system focused. When full, new candidates queue.
32. **Demotion triggers position close.** When a PROMOTED stock is demoted, any open position MUST be sold. Never hold positions in stocks the bot no longer monitors.
33. **90-day cooldown after demotion.** Prevents the screener from re-discovering the same failing stock every week.
