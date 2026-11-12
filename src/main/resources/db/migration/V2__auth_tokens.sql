-- Токены входа. Храним только SHA-256 хеш токена: если кто-то увидит базу,
-- по хешу войти в приложение нельзя.
CREATE TABLE auth_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_tokens_user ON auth_tokens (user_id);
