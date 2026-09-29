CREATE TABLE daily_briefings (
    base_date             DATE         PRIMARY KEY,
    previous_trading_date DATE,
    status                TEXT         NOT NULL CHECK (status IN ('READY', 'PARTIAL')),
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    market                JSONB        NOT NULL,
    headline              JSONB,
    issues                JSONB        NOT NULL,
    themes                JSONB        NOT NULL,
    watch_points          JSONB        NOT NULL,
    risks                 JSONB        NOT NULL,
    analyzed_news         JSONB        NOT NULL,
    relation_graph        JSONB        NOT NULL,
    flagged_snapshot      JSONB        NOT NULL
);
