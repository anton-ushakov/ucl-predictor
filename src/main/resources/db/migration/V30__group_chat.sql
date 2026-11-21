-- Чат группы: разговор, не привязанный к туру.
--
-- Обсуждение тура остаётся там, где оно есть: спор о том, кто как угадал, идёт рядом с
-- таблицей. А договориться о встрече, скинуть ссылку или просто поболтать удобнее в одном
-- месте на всю группу — туры для этого слишком быстро сменяются.
CREATE TABLE group_messages
(
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT        NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    user_id    BIGINT        NOT NULL REFERENCES app_users (id),
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    deleted_at TIMESTAMPTZ
);

-- лента всегда читается по группе, от старых к новым
CREATE INDEX idx_group_messages_group ON group_messages (group_id, id);

-- До какого сообщения участник дочитал: по этому считается, сколько новых.
CREATE TABLE group_chat_reads
(
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES app_users (id),
    last_read_id BIGINT      NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_group_chat_read UNIQUE (group_id, user_id)
);

-- Оповещения о чате включены по умолчанию, но их можно выключить отдельно от обсуждения тура.
ALTER TABLE notification_settings ADD COLUMN chat BOOLEAN NOT NULL DEFAULT TRUE;
