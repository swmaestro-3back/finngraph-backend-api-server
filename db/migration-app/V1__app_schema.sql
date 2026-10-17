CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    nickname   VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE auth_credentials (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(10)  NOT NULL CHECK (provider IN ('KAKAO', 'EMAIL')),
    provider_user_id VARCHAR(255),
    email            VARCHAR(254),
    password_hash    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_credentials_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_auth_credentials_shape CHECK (
        (provider = 'KAKAO' AND provider_user_id IS NOT NULL AND email IS NULL AND password_hash IS NULL)
        OR
        (provider = 'EMAIL' AND email IS NOT NULL AND password_hash IS NOT NULL AND provider_user_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_auth_credentials_kakao ON auth_credentials (provider_user_id) WHERE provider = 'KAKAO';
CREATE UNIQUE INDEX uq_auth_credentials_email ON auth_credentials (email) WHERE provider = 'EMAIL';
