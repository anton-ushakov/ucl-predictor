-- ============================================================
-- V8: оповещения
--   каждый участник сам включает три вида оповещений:
--   час до закрытия приёма, итог матча, итоги дня и тура
-- ============================================================

CREATE TABLE notification_settings (
    user_id        BIGINT PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    lock_soon      BOOLEAN NOT NULL DEFAULT TRUE,
    match_finished BOOLEAN NOT NULL DEFAULT TRUE,
    summary        BOOLEAN NOT NULL DEFAULT TRUE
);

-- Готовые оповещения для участника. dedup_key не даёт прислать одно и то же дважды.
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    group_id   BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    dedup_key  VARCHAR(160) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(600) NOT NULL,
    round_week DATE,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ,
    CONSTRAINT uq_notification UNIQUE (user_id, dedup_key)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
