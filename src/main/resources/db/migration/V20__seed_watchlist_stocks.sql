-- Seed ETFs into watchlist
INSERT INTO watchlist_stocks (symbol, company_name, sector, stock_type, is_etf, status) VALUES
    ('STANBICETF30', 'Stanbic IBTC ETF 30', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('VETGRIF30', 'Vetiva Griffin 30 ETF', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('MERGROWTH', 'Meristem Growth ETF', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('MERVALUE', 'Meristem Value ETF', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('SIAMLETF40', 'SIAML ETF 40', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('NEWGOLD', 'NewGold ETF', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('VETINDETF', 'Vetiva Industrial ETF', 'ETF', 'ETF', TRUE, 'ACTIVE'),
    ('LOTUSHAL15', 'Lotus Halal ETF', 'ETF', 'ETF', TRUE, 'ACTIVE')
ON CONFLICT (symbol) DO NOTHING;

-- Seed large-cap stocks into watchlist
INSERT INTO watchlist_stocks (symbol, company_name, sector, stock_type, is_ngx30, is_pension_eligible, status) VALUES
    ('ZENITHBANK', 'Zenith Bank Plc', 'Financial Services', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('GTCO', 'Guaranty Trust Holding Co', 'Financial Services', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('ACCESSCORP', 'Access Holdings Plc', 'Financial Services', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('UBA', 'United Bank for Africa', 'Financial Services', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('FBNH', 'FBN Holdings Plc', 'Financial Services', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('DANGCEM', 'Dangote Cement Plc', 'Industrial Goods', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('BUACEMENT', 'BUA Cement Plc', 'Industrial Goods', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('SEPLAT', 'Seplat Energy Plc', 'Oil & Gas', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('ARADEL', 'Aradel Holdings Plc', 'Oil & Gas', 'EQUITY', TRUE, TRUE, 'ACTIVE'),
    ('MTNN', 'MTN Nigeria Communications', 'ICT', 'EQUITY', TRUE, TRUE, 'ACTIVE')
ON CONFLICT (symbol) DO NOTHING;
