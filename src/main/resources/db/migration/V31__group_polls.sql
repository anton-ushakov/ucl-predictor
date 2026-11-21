-- Голосования в группе.
--
-- Владелец решает не всё: где-то честнее спросить всех. Любой участник задаёт вопрос, на
-- который отвечают «да» или «нет». Голосование бывает открытым — тогда после завершения
-- видно, кто как ответил, — и закрытым, где виден только итог.
CREATE TABLE group_polls
(
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    author_id  BIGINT       NOT NULL REFERENCES app_users (id),
    question   VARCHAR(200) NOT NULL,
    open_votes BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    closes_at  TIMESTAMPTZ,
    closed_at  TIMESTAMPTZ
);

CREATE INDEX idx_group_polls_group ON group_polls (group_id, id DESC);

CREATE TABLE poll_votes
(
    id         BIGSERIAL PRIMARY KEY,
    poll_id    BIGINT      NOT NULL REFERENCES group_polls (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id),
    yes        BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_poll_vote UNIQUE (poll_id, user_id)
);

-- Оповещения о голосованиях включены по умолчанию и выключаются отдельно.
ALTER TABLE notification_settings ADD COLUMN polls BOOLEAN NOT NULL DEFAULT TRUE;
