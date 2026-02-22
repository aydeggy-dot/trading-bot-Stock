-- Backtest runs table
CREATE TABLE IF NOT EXISTS backtest_runs (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(50) NOT NULL,
    market VARCHAR(5) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital DECIMAL(14,2) NOT NULL,
    final_capital DECIMAL(14,2),
    currency VARCHAR(3) NOT NULL,
    total_return_pct DECIMAL(10,4),
    annualized_return_pct DECIMAL(10,4),
    sharpe_ratio DECIMAL(8,4),
    max_drawdown_pct DECIMAL(10,4),
    win_rate_pct DECIMAL(8,4),
    profit_factor DECIMAL(8,4),
    total_trades INT,
    winning_trades INT,
    losing_trades INT,
    avg_holding_period_days DECIMAL(8,2),
    max_consecutive_losses INT,
    gross_profit DECIMAL(14,2),
    gross_loss DECIMAL(14,2),
    total_commissions DECIMAL(14,2),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Backtest trades table
CREATE TABLE IF NOT EXISTS backtest_trades (
    id BIGSERIAL PRIMARY KEY,
    backtest_run_id BIGINT NOT NULL REFERENCES backtest_runs(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL,
    entry_date DATE NOT NULL,
    exit_date DATE,
    entry_price DECIMAL(12,4) NOT NULL,
    exit_price DECIMAL(12,4),
    quantity INT NOT NULL,
    commission DECIMAL(10,4),
    slippage DECIMAL(10,4),
    gross_pnl DECIMAL(14,2),
    net_pnl DECIMAL(14,2),
    net_pnl_pct DECIMAL(10,4),
    holding_days INT,
    signal_strength VARCHAR(20),
    confidence_score INT,
    exit_reason VARCHAR(50),
    is_open BOOLEAN DEFAULT TRUE
);

-- Equity curve points table
CREATE TABLE IF NOT EXISTS equity_curve_points (
    id BIGSERIAL PRIMARY KEY,
    backtest_run_id BIGINT NOT NULL REFERENCES backtest_runs(id) ON DELETE CASCADE,
    trade_date DATE NOT NULL,
    portfolio_value DECIMAL(14,2) NOT NULL,
    cash_balance DECIMAL(14,2) NOT NULL,
    positions_value DECIMAL(14,2),
    drawdown_pct DECIMAL(10,4),
    daily_return_pct DECIMAL(10,4)
);

-- Indexes
CREATE INDEX idx_backtest_runs_strategy ON backtest_runs(strategy_name);
CREATE INDEX idx_backtest_runs_market ON backtest_runs(market);
CREATE INDEX idx_backtest_runs_status ON backtest_runs(status);
CREATE INDEX idx_backtest_trades_run ON backtest_trades(backtest_run_id);
CREATE INDEX idx_backtest_trades_symbol ON backtest_trades(symbol);
CREATE INDEX idx_equity_curve_run ON equity_curve_points(backtest_run_id);
CREATE INDEX idx_equity_curve_date ON equity_curve_points(backtest_run_id, trade_date);
