-- ============================================================
-- V19: долговая книга шаурмы и автоматический состав тура
-- ============================================================

-- Кто кому должен шаурму. Запись появляется, когда подведены итоги тура;
-- отметку «отдал» ставит получивший или владелец группы.
CREATE TABLE shawarma_debts (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week  DATE        NOT NULL,
    debtor_id   BIGINT      NOT NULL REFERENCES app_users (id),
    creditor_id BIGINT      NOT NULL REFERENCES app_users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    settled_at  TIMESTAMPTZ,
    settled_by  BIGINT REFERENCES app_users (id),
    CONSTRAINT uq_shawarma_debt UNIQUE (group_id, round_week, debtor_id, creditor_id)
);

CREATE INDEX idx_shawarma_debts_group ON shawarma_debts (group_id, settled_at);

-- Долги по уже подведённым турам: каждый проигравший должен каждому победителю.
INSERT INTO shawarma_debts (group_id, round_week, debtor_id, creditor_id, created_at)
SELECT l.group_id, l.round_week, l.user_id, w.user_id, l.finalized_at
  FROM round_results l
  JOIN round_results w
    ON w.group_id = l.group_id AND w.round_week = l.round_week AND w.winner
 WHERE l.loser AND l.user_id <> w.user_id
ON CONFLICT DO NOTHING;

-- Турниры, матчи которых попадают в тур группы сами, без ручного выбора.
CREATE TABLE group_auto_competitions (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    competition_id BIGINT NOT NULL REFERENCES competitions (id),
    CONSTRAINT uq_group_auto_competition UNIQUE (group_id, competition_id)
);
