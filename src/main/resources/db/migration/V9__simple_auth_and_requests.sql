-- ============================================================
-- V9: простой вход по логину и вступление в группу по приглашению или заявке
--
-- Парольная система остаётся в базе и в коде целиком: она просто выключена
-- настройкой app.auth.mode. Вернуть её можно без миграций.
-- ============================================================

-- Приглашения от владельца группы и заявки от желающих вступить.
--   kind = INVITE  : владелец позвал участника, решает приглашённый
--   kind = REQUEST : участник попросился в группу, решает владелец
CREATE TABLE group_requests (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('INVITE', 'REQUEST')),
    status     VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_by BIGINT      NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by BIGINT      REFERENCES app_users (id)
);

-- Одновременно может быть только одно нерешённое приглашение или заявка на пару группа+участник
CREATE UNIQUE INDEX uq_group_request_pending
    ON group_requests (group_id, user_id) WHERE status = 'PENDING';

CREATE INDEX idx_group_requests_user ON group_requests (user_id, status);
CREATE INDEX idx_group_requests_group ON group_requests (group_id, status);
