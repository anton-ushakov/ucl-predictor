-- ============================================================
-- V17: обсуждение тура
--
-- Лента сообщений внутри тура группы: спор о том, кто как угадал,
-- идёт там, где видно таблицу, и не превращается в общую свалку.
-- ============================================================

CREATE TABLE round_messages (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week DATE         NOT NULL,
    user_id    BIGINT       NOT NULL REFERENCES app_users (id),
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    deleted_at TIMESTAMPTZ
);

-- выборка всегда идёт по группе и туру, от старых к новым
CREATE INDEX idx_round_messages_round ON round_messages (group_id, round_week, id);

-- Оповещения об обсуждении включены по умолчанию.
ALTER TABLE notification_settings ADD COLUMN discussion BOOLEAN NOT NULL DEFAULT TRUE;
