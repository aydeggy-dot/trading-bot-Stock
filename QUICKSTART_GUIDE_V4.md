# Quickstart Guide — V4 (Trove + Multi-Market + News Intelligence + Safety Systems Edition)

## What You Got

| File | Purpose | Size |
|------|---------|------|
| `CLAUDE_CODE_PROMPT_V4.md` | Full development spec — paste into Claude Code or rename to CLAUDE.md | ~1,800 lines |
| `CLAUDE_V4.md` | Guard rail — auto-read by Claude Code from project root | ~150 lines |
| `QUICKSTART_GUIDE_V4.md` | This file — setup + workflow instructions | — |

## What Changed from V3

| Area | V3 | V4 |
|------|----|----|
| **Broker** | Bamboo (no retail web portal found) | **Trove** (trovefinance.com — has real web portal at app.trovefinance.com) |
| **Web portal** | Bamboo app.investbamboo.com redirects to mobile app | **Trove app.trovefinance.com** = full JavaScript SPA, login + trade — Playwright confirmed viable |
| **Execution** | Playwright -> Bamboo (broken — no web portal) | **Playwright -> Trove** (working web portal + future API migration path) |
| **Future API** | Bamboo B2B API (docs.investbamboo.com) — B2B only, not accessible | **Trove API** (trovefinance.com/api) — free API keys via Google Form, future option |
| **US broker** | DriveWealth LLC (via Bamboo) | **DriveWealth LLC (via Trove)** — same underlying infrastructure |
| **NGX broker** | Lambeth Capital (via Bamboo) | **Innova Securities** (Trove's own SEC-licensed subsidiary) |
| **All other features** | ✓ (11 strategies, news, settlement, reconciliation, OTP, backtest, etc.) | **Identical** — no features dropped, all V3 safety systems preserved |
| **AI Intelligence** | Not present | **Claude API** — Haiku for bulk article analysis, Sonnet for high-impact events. 5% of total signal weight. ~$3-5/month. Graceful fallback to rule-based. |
| **Stock Discovery** | Static watchlist only (30 tickers) | **Dynamic discovery** — EODHD Screener (weekly) + news mentions + insider buying → observe → promote. Up to 60 active stocks. |

## Prerequisites

### Install Claude Code
```bash
npm install -g @anthropic-ai/claude-code
```

### System Requirements
- Java 21 (Eclipse Temurin recommended)
- Maven 3.9+
- PostgreSQL 16+
- Docker + Docker Compose
- Node.js 18+ (for Claude Code CLI)

### Accounts to Set Up
1. **Trove** — Sign up at trovefinance.com or app.trovefinance.com (Nigerian BVN required)
   - Fund both NGN and USD wallets
   - Note your login credentials for `TROVE_USERNAME` / `TROVE_PASSWORD`
   - Note: you may need to set up 2FA — the bot handles this via WhatsApp OTP prompts
2. **EODHD** — Sign up at eodhd.com for market data API
   - Covers BOTH NGX (.XNSA) and US (.US) tickers on a single key
   - $19.99/month All World plan recommended for multi-market
3. **WAHA** — Free Docker container for WhatsApp automation
4. **Telegram Bot** — Create via @BotFather on Telegram
5. **Anthropic API** — Sign up at console.anthropic.com for Claude API key
   - Fund with $10-15 to cover several months of AI analysis
   - Models used: Haiku (bulk) + Sonnet (high-impact only)
   - Estimated cost: ~$3-5/month at normal article volume

## Project Setup

```bash
# 1. Create project directory
mkdir trading-bot && cd trading-bot

# 2. Copy the files (rename to standard names)
cp path/to/CLAUDE_V4.md ./CLAUDE.md
cp path/to/CLAUDE_CODE_PROMPT_V4.md ./CLAUDE_CODE_PROMPT.md

# 3. Start Claude Code
claude

# Claude Code automatically reads CLAUDE.md on startup.
# You should see it acknowledge the project context.
```

## Build Workflow (16 Phases)

Feed each phase command from `CLAUDE_CODE_PROMPT.md` into Claude Code. After each phase, run `mvn test` to verify.

### Phase 1: Project Scaffold + Database (Week 1-2)
All 18 Flyway migrations (including settlement_ledger, reconciliation_log, order_status_history), entities with market/currency/pool fields, all config classes, Docker Compose.
```
# After Phase 1, verify:
docker compose up -d postgres
mvn spring-boot:run   # Should create all 18 tables
```

### Phase 2: Data Pipeline — Multi-Market (Week 2-3)
EODHD client for both NGX (.XNSA) and US (.US) tickers. FX rate client for USD/NGN. NGX scrapers. US earnings calendar.

### Phase 3: FX Module (Week 3)
Money value object, FxRateService, FxConverter, CurrencyExposureTracker, TroveFxRateCapture stub. Foundational — everything downstream depends on currency-aware calculations.

### Phase 4: Technical Indicators + Fundamental Scorer (Week 4-5)
RSI, MACD, SMA, ATR, volume analysis. FundamentalScorer (0-100). Market-agnostic — operates on OHLCV data.

### Phase 5: News Intelligence Module (Week 5-7)
Build in this order:
1. RSS scrapers (Nairametrics, BusinessDay, Reuters) — Jsoup
2. NGX Bulletin PDF parser (Apache PDFBox) — Form 29 insider trades
3. NewsEventClassifier — 17 EventType regex patterns
4. EventImpactRules — event type -> signal modifier mapping
5. NewsImpactScorer + NewsScraperScheduler
6. Test classifier against sample headlines

### Phase 5b: AI Intelligence Layer (Week 7-8) ★ NEW
Build on top of news module. ClaudeApiClient (WebClient → Anthropic Messages API), AiCostTracker (budget enforcement), AiFallbackHandler (graceful degradation), AiNewsAnalyzer (Haiku for all articles, Sonnet for high-impact), AiEarningsAnalyzer (Sonnet-only), AiCrossArticleSynthesizer (daily), AiInsiderTradeInterpreter. Update NewsImpactScorer to blend rule-based 50% + AI 50%. V19 migration for ai_analysis + ai_cost_ledger tables. **Key: bot must work identically without AI — test fallback thoroughly.**

### Phase 5c: Stock Discovery Module (Week 8-9) ★ NEW
Build dynamic watchlist expansion. EodhdScreenerClient wraps EODHD Screener API (exchange=XNSA for NGX, exchange=US). WatchlistManager merges SEED (YAML) + PROMOTED. CandidateEvaluator runs 3-stage filter (basic → fundamental → optional AI). NewsDiscoveryListener catches news about unknown stocks. PromotionPolicy gates OBSERVATION → PROMOTED (min observation days, buy signal required, fundamental score > 50). DemotionPolicy handles volume drops, red flags, stale stocks. V20 migration. **Key: SEED stocks are PERMANENT — discovery only adds, never removes.**

### Phase 6: All 11 Strategies + Signal Scorer (Week 8-10)
New US strategies: UsEarningsMomentumStrategy + UsEtfRotationStrategy.
CompositeSignalScorer: tech 40%, fundamental 30%, NAV 20%, news 10% (news blends rule-based + AI).

### Phase 7: Risk Management — Pool + Market + Currency + Settlement (Week 9-10)
**This phase is significantly more complex than V2.** Four dimensions of risk:
- Pool: CORE vs SATELLITE
- Market: NGX vs US
- Currency: max 70% in any single currency
- **Settlement: SettlementCashTracker tracks settling vs available cash per market/currency. PositionSizer uses ONLY available cash. This prevents freeriding violations and rejected orders.**

### Phase 8: Long-Term Portfolio Engine — Multi-Currency (Week 10-11)
DCA with separate NGN/USD budgets. Dividend tracker with 30% US withholding tax. Cross-market rebalancing.

### Phase 9: Notifications (Week 11-12)
Currency-aware formatting. New notification types: OTP prompts, order recovery URGENT alerts, reconciliation mismatches, settlement transitions.

### Phase 10: Backtesting Engine (Week 12-13) ★ NEW
**Must complete before going live.** BacktestRunner replays historical data. SimulatedOrderExecutor fills with slippage + commissions + settlement rules. PerformanceAnalyzer produces Sharpe, drawdown, win rate, profit factor. Run every strategy through 6+ months of data. Minimum thresholds: Sharpe > 0.5, profit factor > 1.3, max drawdown < 20%.

### Phase 11: Playwright Execution (Trove) + Reconciliation + Recovery (Week 13-16) ★ CRITICAL
**This is the most complex phase. Build in this order:**

1. **BrowserSessionLock** — ReentrantLock that ALL Playwright methods acquire
2. **OtpHandler** — Detect OTP page, WhatsApp prompt, wait for reply, enter code
3. **TroveBrowserAgent** — Full login + NGX/US trading + portfolio reading
4. **OrderRecoveryService** — UNCERTAIN state, kill switch, screenshot, alert
5. **PortfolioReconciler + CashReconciler + FxRateReconciler** — Startup + daily sync
6. **TroveFxRateCapture** — Scrape actual rate from transaction confirmation
7. **TroveApiClient stub** — Future @ConditionalOnProperty migration

**Before this phase, you MUST discover real selectors:**
```bash
npx playwright codegen https://app.trovefinance.com

# Log in manually, navigate through:
# 1. Login flow (email, password, OTP if any)
# 2. NGX trade flow (search, fill, review, confirm)
# 3. US trade flow (search, fill, review, confirm)
# 4. Portfolio page (NGX + US holdings)
# 5. Transaction history page (for FX rate capture)
# Record all selectors -> update application.yml trove.selectors.*
```

### Phase 12: Dashboard + Monitoring (Week 16-17)
Multi-market REST API including settlement status, reconciliation reports, FX spread cost, backtest results.

### Phase 13: Integration Testing (Week 17-18) ★ NEW
End-to-end tests covering the full signal->risk->execution flow, settlement prevention, reconciliation detection, OTP flow, order recovery, cross-market circuit breakers.

### Phase 14: Docker + Deployment (Week 18-19)
Production build. Verify reconciliation runs on container restart. Persistent volumes for screenshots and settlement ledger.

## Manual Steps Required

| Step | When | What |
|------|------|------|
| Trove selector discovery | Before Phase 11 | Run `playwright codegen` against Trove web portal |
| Anthropic API key | Before Phase 5b | Sign up at console.anthropic.com, fund $10-15 |
| WAHA QR scan | Before Phase 9 | Scan WhatsApp QR code to link WAHA session |
| EODHD API signup | Before Phase 2 | Get API key at eodhd.com ($19.99/mo All World plan) |
| Trove account funding | Before going live | Fund NGN and USD wallets |
| Backtest validation | Before going live | Run ALL strategies, verify Sharpe > 0.5 per strategy |
| Trove API access request (optional) | When ready | Apply at trovefinance.com/api when ready for API migration |

## Future API Migration (When Ready)

When Trove API access is pursued (future option):

```yaml
# application.yml — just flip this:
trove:
  api:
    enabled: true
    api-key: your-key-here
    api-secret: your-secret-here
```

Spring Boot's `@ConditionalOnProperty` automatically activates `TroveApiClient` and deactivates `TroveBrowserAgent`. The `BrokerGateway` interface ensures zero changes needed in OrderManager, OrderRouter, RiskManager, ReconciliationScheduler, or any upstream service. BrowserSessionLock and OtpHandler become dormant (API doesn't need them). Settlement tracking and reconciliation continue unchanged.

## Cost Estimate (Monthly)

| Item | Cost |
|------|------|
| Hetzner VPS (CX21 — 2 vCPU, 4GB RAM) | ~N11,400 ($7.50) |
| EODHD All World plan | ~N30,000 ($19.99) |
| WAHA (Docker container) | Free |
| Trove trading commissions | Commission-free US / ~0.5% NGX per trade |
| **Anthropic Claude API (AI analysis)** | **~$3-5/month** (hard-capped at $10/month) |
| **Trove FX spread (hidden cost)** | **~TBD% on NGN->USD conversion (capture from day one)** |
| **Total fixed** | **~N48,000/month + FX spread** |

Note: Trove's FX spread may differ from other brokers — capture and track it from day one. The bot tracks this separately in performance reports so you always know the true cost.

## Architecture Decisions Summary

1. **Single broker (Trove)** -> One login, one Playwright agent, one execution engine. Trove has a real web portal at app.trovefinance.com (unlike Bamboo which is mobile-only for retail).
2. **Playwright first, API later** -> BrokerGateway interface makes the switch a config change. Trove also offers API access (trovefinance.com/api) for future migration.
3. **News as signal modifier (10% weight)** -> Informs but never drives trades. Rule-based classification (Level 2) + AI deep analysis (Level 3, Claude API). AI adds nuance regex misses — but controls only 5% of total signal.
4. **Separate DCA days per market** -> NGX on 5th, US on 10th. Spreads FX conversion load.
5. **NGN as base currency** -> All portfolio views in naira. FX rate captured at every US trade entry.
6. **30% US dividend tax baked in** -> Position sizer uses net yield, never gross.
7. **13-hour operational window** -> NGX morning, 1-hour gap, US evening. Single VPS handles both.
8. **Settlement cash tracking** -> Prevents freeriding violations and order rejections. The #1 cause of production failures in trading bots.
9. **Reconciliation on every startup** -> Bot always starts from ground truth. Catches all drift from crashes, manual trades, corporate actions.
10. **BrowserSessionLock** -> One browser, many concurrent tasks. Lock prevents race conditions that corrupt data or submit wrong orders.
11. **OTP via WhatsApp** -> Turns a blocking production problem into a 2-minute human-in-the-loop flow.
12. **Order recovery with UNCERTAIN state** -> Network failures happen. The bot doesn't guess — it halts and asks.
13. **Dual FX rate tracking** -> Market rate for benchmarking, Trove rate for real P&L. Honesty about costs.
14. **Backtesting before live** -> No strategy trades real money until it proves profitable on historical data.
15. **AI as enrichment, not dependency** -> Claude API (Haiku bulk ~$0.02/day, Sonnet high-impact ~$0.06/day) adds nuance to news analysis. Hard budget caps ($0.50/day, $10/month). Bot works identically without AI — graceful degradation on any failure. Never in trade execution path.
16. **Dynamic stock discovery with safety rails** -> EODHD Screener finds candidates weekly. Stocks must pass fundamental filters + observation period (7-14 days) + get at least 1 buy signal before trading. SEED stocks are permanent. Max 60 active stocks caps API costs. Demotion auto-closes positions and enforces 90-day cooldown.
