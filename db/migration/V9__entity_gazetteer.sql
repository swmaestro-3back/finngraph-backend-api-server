CREATE TABLE IF NOT EXISTS entity_gazetteer (
    alias          TEXT        PRIMARY KEY,
    company_id     BIGINT      NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    stock_id       BIGINT      NOT NULL REFERENCES stocks (id) ON DELETE CASCADE,
    ticker         VARCHAR(20) NOT NULL,
    canonical_name TEXT        NOT NULL,
    alias_source   TEXT        NOT NULL,
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS entity_gazetteer_company_idx ON entity_gazetteer (company_id);
