CREATE TABLE system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    updated_by VARCHAR(50) DEFAULT 'SYSTEM',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Seed default system config
INSERT INTO system_config (config_key, config_value, description) VALUES
    ('portfolio.initial_capital', '5000000.00', 'Initial portfolio capital in NGN'),
    ('portfolio.available_cash', '5000000.00', 'Currently available cash in NGN'),
    ('portfolio.settling_cash', '0.00', 'Cash from sales pending T+2 settlement'),
    ('bot.version', '1.0.0', 'Current bot version'),
    ('bot.last_health_check', '', 'Timestamp of last health check');
