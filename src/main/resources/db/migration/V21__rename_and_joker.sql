-- ============================================================
-- V21: джокер — удвоение баллов на одном матче тура
-- ============================================================

-- Включается в настройках группы. Выключение не отменяет уже выбранные джокеры:
-- правила тура, который идёт, менять задним числом нечестно.
ALTER TABLE friend_groups ADD COLUMN joker_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Один матч на участника и тур: за него баллы считаются вдвойне.
CREATE TABLE round_jokers (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week DATE        NOT NULL,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    match_id   BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_round_joker UNIQUE (group_id, round_week, user_id)
);

CREATE INDEX idx_round_jokers_round ON round_jokers (group_id, round_week);
