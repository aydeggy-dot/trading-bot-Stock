CREATE TABLE sector_mapping (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    sector VARCHAR(50) NOT NULL,
    sub_sector VARCHAR(50),
    is_ngx30 BOOLEAN DEFAULT FALSE,
    is_pension_eligible BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_sector_mapping_sector ON sector_mapping(sector);

-- Seed initial sector mappings for large-caps and ETFs
INSERT INTO sector_mapping (symbol, sector, sub_sector, is_ngx30, is_pension_eligible) VALUES
    ('ZENITHBANK', 'Financial Services', 'Banking', TRUE, TRUE),
    ('GTCO', 'Financial Services', 'Banking', TRUE, TRUE),
    ('ACCESSCORP', 'Financial Services', 'Banking', TRUE, TRUE),
    ('UBA', 'Financial Services', 'Banking', TRUE, TRUE),
    ('FBNH', 'Financial Services', 'Banking', TRUE, TRUE),
    ('DANGCEM', 'Industrial Goods', 'Cement', TRUE, TRUE),
    ('BUACEMENT', 'Industrial Goods', 'Cement', TRUE, TRUE),
    ('SEPLAT', 'Oil & Gas', 'Exploration & Production', TRUE, TRUE),
    ('ARADEL', 'Oil & Gas', 'Exploration & Production', TRUE, TRUE),
    ('MTNN', 'ICT', 'Telecommunications', TRUE, TRUE),
    ('STANBICETF30', 'ETF', 'Index ETF', FALSE, FALSE),
    ('VETGRIF30', 'ETF', 'Index ETF', FALSE, FALSE),
    ('MERGROWTH', 'ETF', 'Growth ETF', FALSE, FALSE),
    ('MERVALUE', 'ETF', 'Value ETF', FALSE, FALSE),
    ('SIAMLETF40', 'ETF', 'Index ETF', FALSE, FALSE),
    ('NEWGOLD', 'ETF', 'Commodity ETF', FALSE, FALSE),
    ('VETINDETF', 'ETF', 'Index ETF', FALSE, FALSE),
    ('LOTUSHAL15', 'ETF', 'Halal ETF', FALSE, FALSE);
