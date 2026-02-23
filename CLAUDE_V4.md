# CLAUDE.md — Multi-Market AI Trading Bot Project Context

## What This Project Is
A Spring Boot application managing a stock portfolio across BOTH the Nigerian Stock Exchange (NGX) AND US markets (NYSE/NASDAQ) through a single broker — **Trove** (trovefinance.com). Uses Playwright-Java for browser automation against app.trovefinance.com (with a designed-in migration path to Trove's REST API when access is pursued). Includes news intelligence, WhatsApp + Telegram notifications, and a Core-Satellite portfolio structure covering short-term active trading AND long-term wealth building.

## Tech Stack
- Java 21, Spring Boot 3.3+, Maven
- PostgreSQL 16 + Flyway migrations (V1-V20)
- Playwright-Java (com.microsoft.playwright) for Trove browser automation
- Anthropic Claude API (Haiku for bulk, Sonnet for high-impact) for AI news analysis
- WAHA (Docker) for WhatsApp, Telegram Bot API as fallback
- Jsoup for web scraping + Apache PDFBox for NGX bulletin parsing
- WebClient for HTTP calls, Lombok, JUnit 5, Mockito, AssertJ

## Single Broker: Trove
- ONE login, ONE execution engine for both NGX and US trades
- NGX via Innova Securities (SEC-Nigeria licensed, Trove's subsidiary)
- US via DriveWealth LLC (FINRA/SIPC insured up to $500,000)
- Current: Playwright browser automation against app.trovefinance.com
- Future: flip `trove.api.enabled=true` -> TroveApiClient activates via @ConditionalOnProperty
- BrokerGateway interface abstracts the switch — zero architecture changes needed

## Two Markets, Two Currencies, One Base
- **NGX:** WAT (Africa/Lagos), 10:00-14:30, NGN, T+2 settlement, +/-10% daily limit
- **US:** EST (America/New_York), 09:30-16:00, NGN equivalent 15:30-22:00 WAT, USD, T+1 settlement
- **Base currency:** NGN — all values roll up to naira for unified reporting
- **TWO FX rates tracked per US trade:** market rate (EODHD) + Trove's actual rate (includes spread)
- **Bot operational window:** 09:00 - 22:00 WAT (13 hours)

## Core-Satellite Portfolio Model
- **CORE (60-70%):** Long-term. NGX dividend banks + US index ETFs (VOO, SCHD, BND, GLD). DCA monthly. Quarterly rebalancing. Wide 25% stops. NO circuit breakers. Dividend reinvestment.
- **SATELLITE (30-40%):** Active trading. NGX ETF arbitrage + momentum. US earnings momentum + sector ETF rotation. Tight 8-10% stops. Circuit breakers active. Signal-driven.
- **CRITICAL:** Core and satellite tracked SEPARATELY. A stock can appear in BOTH pools. Risk rules differ per pool AND per market.

## Nine Critical Safety Systems

### 1. Settlement Cash Tracking (SettlementCashTracker)
- NGX settles T+2, US settles T+1. Cash from sells is NOT immediately available.
- PositionSizer MUST use `getAvailableCash()`, NEVER total cash.
- Every sell creates a settlement_ledger entry with `settles_on` date.
- Spending unsettled US funds = freeriding violation = 90-day account freeze.

### 2. Portfolio Reconciliation (PortfolioReconciler)
- Runs on EVERY startup + daily at 09:00 WAT before trading.
- Scrapes Trove's actual portfolio, compares to internal DB state.
- Position mismatch -> HALT all trading + WhatsApp alert until resolved.
- Catches: manual trades on Trove app, fills during bot downtime, corporate actions.

### 3. Browser Session Lock (BrowserSessionLock)
- ALL Playwright operations acquire a ReentrantLock before touching the browser.
- Prevents race conditions between stop-loss monitor, order execution, reconciliation, news scraping.
- One browser, many scheduled tasks -> must serialize access.

### 4. OTP/2FA Handling (OtpHandler)
- Trove likely requires OTP from headless server environments.
- On OTP detection: sends WhatsApp asking for code, waits up to 120s for reply.
- Without this, bot cannot log in at all in production.
- OTP flow holds BrowserSessionLock — no other task can use browser while waiting.

### 5. Backtesting Engine (BacktestRunner)
- Every strategy must prove positive EV on historical data before going live.
- Minimum thresholds: Sharpe > 0.5, profit factor > 1.3, max drawdown < 20%.
- SimulatedOrderExecutor respects slippage, commissions, and settlement rules.

### 6. Order Recovery (OrderRecoveryService)
- On ANY Playwright failure mid-order: mark UNCERTAIN, set kill switch, screenshot, alert.
- Attempts to check order history to determine if order went through.
- Never assumes a failed Playwright call means the order failed.

### 7. Trove FX Rate Capture (TroveFxRateCapture)
- Trove's FX rate differs from market rate (their spread).
- Every US trade captures the ACTUAL rate from Trove's confirmation page.
- P&L calculations use Trove's rate (the real cost), not market rate.
- FX spread cost tracked as a separate line item in performance reports.

### 8. AI Intelligence Layer (Claude API) — Enrichment, NOT Dependency
- Adds deep analysis on top of existing rule-based news classification (Level 1+2 unchanged).
- **Tiered models:** Haiku for every article (~$0.0001/article), Sonnet only for high-impact events or low-confidence Haiku results.
- **Cost capped:** $0.50/day, $10/month hard limits. AiCostTracker checks budget before EVERY call.
- **Graceful degradation:** If API is down, budget exceeded, or JSON malformed → falls back to rule-based only. Bot functions identically without AI.
- **AI controls only 5% of total signal weight** (50% of the 10% news component). Cannot cause trades by itself.
- **Never in the execution path.** AI runs async, batched every 15 min — never blocks order placement.
- Components: AiNewsAnalyzer, AiEarningsAnalyzer (Sonnet-only), AiCrossArticleSynthesizer (daily), AiInsiderTradeInterpreter.

### 9. Stock Discovery Module (Dynamic Watchlist)
- Bot finds new opportunities via EODHD Screener API (weekly), news mentions, and insider buying patterns.
- Pipeline: CANDIDATE → OBSERVATION (7-14 day data collection) → PROMOTED (strategies can trade) or DEMOTED.
- **SEED stocks (YAML) are PERMANENT** — discovery only adds, never removes originals.
- Size-capped: max 60 active stocks (SEED + PROMOTED), max 20 in observation.
- Demotion auto-closes positions and triggers 90-day cooldown.
- WatchlistManager.getActiveWatchlist() is the SINGLE source of truth for all strategies.

## Non-Negotiable Code Rules
- All monetary values: `BigDecimal` with `Currency` context — use `Money` value object
- All entities with financial data MUST have `market` (NGX/US) and `currency` (NGN/USD) fields
- Every Position, TradeOrder, TradeSignal MUST have `market`, `currency`, `pool` fields
- Every US trade MUST record both `fx_rate` (market) and `broker_fx_rate` (actual)
- All dates: `ZonedDateTime` with market-specific timezone
- Constructor injection ONLY — no field @Autowired
- Every @Scheduled task specifies `zone` — Africa/Lagos for NGX tasks, America/New_York for US tasks
- All Trove selectors from application.yml — NEVER hardcoded
- ALL Playwright methods MUST acquire BrowserSessionLock
- NGX orders: LIMIT only, always
- US orders: LIMIT preferred, market OK only for liquid ETF DCA
- Kill switch halts BOTH markets instantly
- `MarketHoursUtil.isMarketOpen(Market market)` — checked before EVERY trade
- PositionSizer calls `SettlementCashTracker.getAvailableCash()` — never total cash
- All ClaudeApiClient calls wrapped in AiFallbackHandler — never throw on API failure
- AiCostTracker.checkBudget() before every AI call — no exceptions
- WatchlistManager.getActiveWatchlist() is the ONLY source of tradeable stocks — never query DB directly
- SEED stocks from YAML must NEVER be demoted — hardcoded guard in DemotionPolicy

## Risk Rules — SATELLITE
- Max 2% of satellite value risk per trade
- Max 15% single position
- Max 40% sector exposure
- 5% daily loss -> circuit breaker, 10% weekly -> circuit breaker
- NGX min volume: 10,000 | US min volume: 100,000
- Max 10% of daily volume per order

## Risk Rules — CORE
- Max 20% single position (wider)
- Max 45% sector exposure
- 25% stop-loss (very wide)
- NO circuit breakers
- NGX min dividend yield: 4% | US min dividend yield: 2.5%
- Min fundamental score: 60/100
- Sell ONLY on fundamental deterioration

## Risk Rules — GLOBAL
- Core: 55-75% of total | Satellite: 25-45% of total
- Max single currency (NGN or USD): 70% of total
- Max single market (NGX or US): 75% of total
- Target geographic split: 50% NGX / 50% US
- US dividends: 30% withholding tax — always use NET yield
- Cross-market circuit breaker: if BOTH markets down >5% same day -> global alert
- Reconciliation mismatch -> HALT trading until resolved

## News Intelligence
- News = signal MODIFIER, not signal generator. Capped at 10% of composite signal weight.
- Level 1: Keyword matching on RSS feeds -> instant WhatsApp alerts
- Level 2: Rule-based event classification (regex) -> signal confidence modifiers
- **Level 3: AI deep analysis (Claude API)** -> sentiment, nuance, forward-looking implications
- News 10% component blends: rule-based 50% + AI 50% (= AI is 5% of total signal)
- Falls back to 100% rule-based if AI unavailable (cost cap, API down, JSON error)
- NGX sources: Nairametrics, BusinessDay, NGX Daily Bulletin (PDF), CBN Press
- US sources: Reuters, Seeking Alpha
- Insider trade detection from NGX bulletin Form 29 (>N10M purchases flagged)
- CompositeSignalScorer: Technical 40% + Fundamental 30% + NAV Discount 20% + News 10%

## 11 Trading Strategies
1. ETF NAV Arbitrage (SATELLITE, NGX)
2. Momentum Breakout (SATELLITE, BOTH)
3. US Earnings Momentum (SATELLITE, US) — buy on positive earnings surprise
4. US ETF Sector Rotation (SATELLITE, US) — monthly top-3 sector rotation
5. Pension Flow Overlay (BOTH pools, NGX)
6. Dividend Accumulation (CORE, BOTH) — 30% US withholding tax aware
7. Value Accumulation (CORE, BOTH) — fundamental scoring
8. Dollar-Cost Averaging (CORE, BOTH) — N150K NGX/month + $300 US/month
9. Currency Hedge (CORE, BOTH) — NEWGOLD + GLD
10. Sector Rotation (CORE, BOTH) — quarterly
11. Pension Flow Overlay (BOTH, NGX)

## Common Commands
```bash
mvn clean compile
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dev
docker compose up -d
```

## Full Specification
See `CLAUDE_CODE_PROMPT.md` for: complete package structure, all 20 Flyway migrations, strategy logic, news classifier event types, AI intelligence layer spec (Claude API tiering, cost controls, fallback), stock discovery module (screener, observation, promotion/demotion), FX module, settlement tracking spec, reconciliation spec, OTP handler spec, order recovery spec, backtest engine spec, scheduling (32 tasks), 16 phased build instructions, Trove selector config.
