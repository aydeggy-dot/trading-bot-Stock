-- AI Cost Ledger: tracks per-call costs for Anthropic API usage
CREATE TABLE ai_cost_ledger (
    id              BIGSERIAL       PRIMARY KEY,
    call_date       DATE            NOT NULL,
    model           VARCHAR(100)    NOT NULL,
    input_tokens    INTEGER         NOT NULL DEFAULT 0,
    output_tokens   INTEGER         NOT NULL DEFAULT 0,
    cost_usd        NUMERIC(10, 6)  NOT NULL DEFAULT 0,
    purpose         VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_cost_ledger_call_date ON ai_cost_ledger (call_date);
CREATE INDEX idx_ai_cost_ledger_model ON ai_cost_ledger (model);

-- AI Analysis: stores AI-generated analysis results for news, earnings, cross-article, and insider trades
CREATE TABLE ai_analysis (
    id                   BIGSERIAL       PRIMARY KEY,
    news_item_id         BIGINT          REFERENCES news_items(id),
    symbol               VARCHAR(20),
    model                VARCHAR(100),
    sentiment            VARCHAR(20),
    confidence_score     INTEGER,
    summary              TEXT,
    key_insights         TEXT,
    predicted_impact     VARCHAR(20),
    sector_implications  TEXT,
    forward_guidance     TEXT,
    management_tone      VARCHAR(50),
    revenue_quality      VARCHAR(50),
    analysis_type        VARCHAR(30),
    input_tokens         INTEGER         DEFAULT 0,
    output_tokens        INTEGER         DEFAULT 0,
    cost_usd             NUMERIC(10, 6)  DEFAULT 0,
    created_at           TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_analysis_news_item_id ON ai_analysis (news_item_id);
CREATE INDEX idx_ai_analysis_symbol ON ai_analysis (symbol);
CREATE INDEX idx_ai_analysis_created_at ON ai_analysis (created_at);
CREATE INDEX idx_ai_analysis_type ON ai_analysis (analysis_type);
