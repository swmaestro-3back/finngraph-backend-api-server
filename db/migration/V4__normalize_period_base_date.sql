DELETE FROM stock_candles_period AS p
 USING stock_candles_period AS q
 WHERE p.stock_id = q.stock_id AND p.period = q.period
   AND date_trunc(CASE p.period WHEN 'W' THEN 'week' ELSE 'month' END, p.base_date)
     = date_trunc(CASE q.period WHEN 'W' THEN 'week' ELSE 'month' END, q.base_date)
   AND (p.updated_at, p.base_date) < (q.updated_at, q.base_date);

UPDATE stock_candles_period
   SET base_date = date_trunc(CASE period WHEN 'W' THEN 'week' ELSE 'month' END, base_date)::date
 WHERE base_date <> date_trunc(CASE period WHEN 'W' THEN 'week' ELSE 'month' END, base_date)::date;
