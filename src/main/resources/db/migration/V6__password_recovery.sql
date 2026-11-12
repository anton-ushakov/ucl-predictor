-- ============================================================
-- V6: восстановление доступа к учётной записи
--   1) код восстановления: выдаётся при регистрации, с ним можно задать новый пароль
--   2) сброс пароля владельцем группы: временный пароль, который нужно сразу сменить
-- ============================================================

-- Хеш кода восстановления (сам код в базе не хранится, как и пароль).
ALTER TABLE app_users ADD COLUMN recovery_hash VARCHAR(100);
ALTER TABLE app_users ADD COLUMN recovery_set_at TIMESTAMPTZ;

-- Пароль выдан владельцем группы и действует до первой смены.
ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- Защита кода восстановления от перебора.
ALTER TABLE app_users ADD COLUMN recovery_fails INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN recovery_blocked_until TIMESTAMPTZ;

-- Кто и когда сбрасывал пароль участнику: чтобы это не прошло незаметно.
CREATE TABLE password_resets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    reset_by   BIGINT      NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_password_resets_user ON password_resets (user_id);
