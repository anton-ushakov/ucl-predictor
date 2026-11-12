-- ============================================================
-- V12: PIN-код для входа в простом режиме
--
-- Вход по одному логину был удобен, но позволял любому, кто знает чужое имя,
-- зайти под ним. Короткий PIN закрывает это: его вводят один раз на телефоне,
-- дальше работает сохранённый токен.
--
-- В базе хранится только хеш, как и у пароля.
-- ============================================================

ALTER TABLE app_users ADD COLUMN pin_hash VARCHAR(100);
ALTER TABLE app_users ADD COLUMN pin_set_at TIMESTAMPTZ;

-- Участник обязан задать PIN при следующем входе (новый или сброшенный владельцем группы)
ALTER TABLE app_users ADD COLUMN must_set_pin BOOLEAN NOT NULL DEFAULT FALSE;

-- Защита от подбора
ALTER TABLE app_users ADD COLUMN pin_fails INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN pin_blocked_until TIMESTAMPTZ;

-- Кто и когда сбрасывал PIN участнику
CREATE TABLE pin_resets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    reset_by   BIGINT      NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_pin_resets_user ON pin_resets (user_id);
