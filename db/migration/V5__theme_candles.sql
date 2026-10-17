CREATE TABLE IF NOT EXISTS theme_candles_daily (
    theme_id    BIGINT  NOT NULL REFERENCES themes (id) ON DELETE CASCADE,
    trade_date  DATE    NOT NULL,
    open        NUMERIC NOT NULL,
    high        NUMERIC NOT NULL,
    low         NUMERIC NOT NULL,
    close       NUMERIC NOT NULL,
    volume      BIGINT  NOT NULL,
    trade_value BIGINT,
    source      TEXT    NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (theme_id, trade_date)
);

CREATE INDEX IF NOT EXISTS theme_candles_daily_date_idx ON theme_candles_daily (trade_date);

CREATE TABLE IF NOT EXISTS theme_candles_period (
    theme_id    BIGINT  NOT NULL REFERENCES themes (id) ON DELETE CASCADE,
    period      TEXT    NOT NULL,
    base_date   DATE    NOT NULL,
    open        NUMERIC NOT NULL,
    high        NUMERIC NOT NULL,
    low         NUMERIC NOT NULL,
    close       NUMERIC NOT NULL,
    volume      BIGINT  NOT NULL,
    trade_value BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (theme_id, period, base_date)
);
