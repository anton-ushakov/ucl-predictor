-- ============================================================
-- V3: туры (недели), роли и настройки групп, история туров,
--     защита прогнозов от изменения
-- ============================================================

-- ---------- Матчи: неделя тура и данные из football-data.org ----------
ALTER TABLE matches ADD COLUMN round_week DATE;
ALTER TABLE matches ADD COLUMN stage VARCHAR(40);
ALTER TABLE matches ADD COLUMN matchday INTEGER;

-- round_week: понедельник той недели (по московскому времени), в которую начинается матч
UPDATE matches
SET round_week = (kickoff_at AT TIME ZONE 'Europe/Moscow')::date
                 - (EXTRACT(ISODOW FROM (kickoff_at AT TIME ZONE 'Europe/Moscow'))::int - 1);

ALTER TABLE matches ALTER COLUMN round_week SET NOT NULL;
CREATE INDEX idx_matches_round_week ON matches (round_week);

-- ---------- Группы: настройки по умолчанию ----------
-- Для тура настройки "замораживаются" в таблице round_rules, когда в группе
-- сохраняется первый прогноз на этот тур (см. ниже).
ALTER TABLE friend_groups
    ADD COLUMN lock_minutes_before INTEGER     NOT NULL DEFAULT 0
        CHECK (lock_minutes_before BETWEEN 0 AND 1440);
ALTER TABLE friend_groups
    ADD COLUMN tiebreakers VARCHAR(200) NOT NULL DEFAULT 'EXACT_SCORES';
ALTER TABLE friend_groups
    ADD COLUMN reveal_mode VARCHAR(20)  NOT NULL DEFAULT 'AFTER_OWN_SUBMIT';

-- ---------- Участники: роль и дата вступления ----------
ALTER TABLE group_members ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE group_members ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Для уже созданных групп владельцем становится участник с наименьшим id
-- (при тестировании это тот, кто создавал группу первым).
UPDATE group_members gm
SET role = 'OWNER'
WHERE gm.user_id = (SELECT MIN(x.user_id) FROM group_members x WHERE x.group_id = gm.group_id);

-- ---------- Правила тура, зафиксированные для группы ----------
CREATE TABLE round_rules (
    id                  BIGSERIAL PRIMARY KEY,
    group_id            BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week          DATE         NOT NULL,
    lock_minutes_before INTEGER      NOT NULL,
    tiebreakers         VARCHAR(200) NOT NULL,
    reveal_mode         VARCHAR(20)  NOT NULL,
    frozen_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_round_rules UNIQUE (group_id, round_week)
);

-- ---------- Прогнозы теперь принадлежат группе ----------
-- До этой миграции API прогнозов не существовало, поэтому таблица пуста.
DELETE FROM predictions;

ALTER TABLE predictions
    ADD COLUMN group_id BIGINT NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE;
ALTER TABLE predictions DROP CONSTRAINT uq_prediction_user_match;
ALTER TABLE predictions
    ADD CONSTRAINT uq_prediction_group_user_match UNIQUE (group_id, user_id, match_id);
CREATE INDEX idx_predictions_group_match ON predictions (group_id, match_id);

-- Сохранённый прогноз нельзя изменить ни через приложение, ни вручную в базе:
-- любой UPDATE отклоняется. (Удаление возможно только каскадно, при удалении
-- пользователя или группы.)
CREATE FUNCTION forbid_prediction_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Сохранённый прогноз нельзя изменить';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER predictions_no_update
    BEFORE UPDATE ON predictions
    FOR EACH ROW EXECUTE FUNCTION forbid_prediction_update();

-- ---------- История: итоги завершённых туров ----------
-- Строка на участника в каждом завершённом туре группы. Это снимок: если потом
-- поменять настройки, итоги прошлых туров не изменятся.
CREATE TABLE round_results (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week     DATE         NOT NULL,
    user_id        BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    points         INTEGER      NOT NULL,
    exact_scores   INTEGER      NOT NULL,
    outcome_hits   INTEGER      NOT NULL,
    goal_diff_hits INTEGER      NOT NULL,
    goal_error     INTEGER      NOT NULL,
    submitted_at   TIMESTAMPTZ,
    place          INTEGER      NOT NULL,
    winner         BOOLEAN      NOT NULL,
    loser          BOOLEAN      NOT NULL,
    tiebreakers    VARCHAR(200) NOT NULL,
    finalized_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_round_result UNIQUE (group_id, round_week, user_id)
);

CREATE INDEX idx_round_results_group ON round_results (group_id, round_week);
