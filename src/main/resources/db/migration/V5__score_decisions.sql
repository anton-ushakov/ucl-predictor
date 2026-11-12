-- ============================================================
-- V5: вид матча (основное время / добавочное / пенальти) и решения группы
--     о матчах, по которым нет полных данных
-- ============================================================

-- REGULAR, EXTRA_TIME или PENALTY_SHOOTOUT. null у старых записей: считаем, что это REGULAR.
ALTER TABLE matches ADD COLUMN duration VARCHAR(20);

-- Решение владельца группы по матчу, у которого нет данных о добавочном времени или пенальти:
--   PROPOSED: посчитать матч только по основному времени (нужно согласие остальных участников тура)
--   DECLINED: владелец не согласен, матч не учитывается при подведении итогов
CREATE TABLE score_decisions (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    match_id    BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    status      VARCHAR(10) NOT NULL CHECK (status IN ('PROPOSED', 'DECLINED')),
    proposed_by BIGINT      NOT NULL REFERENCES app_users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_score_decision UNIQUE (group_id, match_id)
);

-- Голоса участников тура по предложению владельца. Голос окончательный.
CREATE TABLE score_decision_votes (
    id          BIGSERIAL PRIMARY KEY,
    decision_id BIGINT      NOT NULL REFERENCES score_decisions (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    agree       BOOLEAN     NOT NULL,
    voted_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_score_decision_vote UNIQUE (decision_id, user_id)
);
