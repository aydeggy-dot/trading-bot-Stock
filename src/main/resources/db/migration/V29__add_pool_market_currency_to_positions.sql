ALTER TABLE positions ADD COLUMN IF NOT EXISTS market VARCHAR(5);
ALTER TABLE positions ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
ALTER TABLE positions ADD COLUMN IF NOT EXISTS pool VARCHAR(10);
ALTER TABLE positions ADD COLUMN IF NOT EXISTS fx_rate_at_entry DECIMAL(10,4);
ALTER TABLE positions ADD COLUMN IF NOT EXISTS target_weight_pct DECIMAL(6,2);

-- Default existing positions to NGX/NGN/SATELLITE
UPDATE positions SET market = 'NGX' WHERE market IS NULL;
UPDATE positions SET currency = 'NGN' WHERE currency IS NULL;
UPDATE positions SET pool = 'SATELLITE' WHERE pool IS NULL;
