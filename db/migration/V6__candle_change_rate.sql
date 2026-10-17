ALTER TABLE stock_candles_daily  ADD COLUMN IF NOT EXISTS change_rate NUMERIC;
ALTER TABLE stock_candles_period ADD COLUMN IF NOT EXISTS change_rate NUMERIC;
ALTER TABLE theme_candles_daily  ADD COLUMN IF NOT EXISTS change_rate NUMERIC;
ALTER TABLE theme_candles_period ADD COLUMN IF NOT EXISTS change_rate NUMERIC;
