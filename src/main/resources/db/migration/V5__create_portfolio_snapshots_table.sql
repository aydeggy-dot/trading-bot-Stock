CREATE TABLE portfolio_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    total_value DECIMAL(14,2) NOT NULL,
    cash_balance DECIMAL(14,2),
    equity_value DECIMAL(14,2),
    daily_pnl DECIMAL(14,2),
    daily_pnl_pct DECIMAL(8,4),
    weekly_pnl DECIMAL(14,2),
    open_positions_count INTEGER,
    win_rate DECIMAL(6,2),
    profit_factor DECIMAL(8,4),
    max_drawdown_pct DECIMAL(8,4),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(snapshot_date)
);

CREATE INDEX idx_snapshots_date ON portfolio_snapshots(snapshot_date DESC);
