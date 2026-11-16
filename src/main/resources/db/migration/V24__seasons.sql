-- ============================================================
-- V24: сезоны группы
-- ============================================================

-- Общий зачёт за всё время со временем превращается в список, который никто не догонит.
-- Сезон — это отрезок истории: зачёт считается от его начала. Туры никуда не деваются,
-- меняется только то, с какой недели их складывать.
CREATE TABLE group_seasons (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    start_week DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT REFERENCES app_users (id),
    CONSTRAINT uq_group_season UNIQUE (group_id, start_week)
);

CREATE INDEX idx_group_seasons_group ON group_seasons (group_id, start_week);
