CREATE TABLE favorites (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type VARCHAR(10)  NOT NULL CHECK (target_type IN ('STOCK', 'THEME')),
    target_key  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_favorites_user_target UNIQUE (user_id, target_type, target_key)
);

CREATE INDEX idx_favorites_user_created ON favorites (user_id, created_at DESC);
