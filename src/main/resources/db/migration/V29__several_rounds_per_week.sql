-- Несколько туров в одной неделе.
--
-- Раньше тур и неделя были одним и тем же: ключом служил понедельник. Теперь в неделе может
-- быть несколько туров — скажем, Лига чемпионов во вторник и среду отдельным зачётом, а
-- чемпионаты в выходные другим. Ключом тура остаётся дата: для первого тура недели это её
-- понедельник (поэтому все уже сыгранные туры остаются как были), для второго — вторник той
-- же недели, и так далее. Это именно ключ: даты, которые видит участник, берутся из матчей
-- тура, а неделя тура всегда вычисляется как понедельник его ключа.
CREATE TABLE group_rounds
(
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_key  DATE        NOT NULL,
    title      VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT REFERENCES app_users (id),
    CONSTRAINT uq_group_round UNIQUE (group_id, round_key)
);

CREATE INDEX idx_group_rounds_group ON group_rounds (group_id, round_key);

-- Уже существующие туры: всё, где есть отобранные матчи или подведённые итоги.
INSERT INTO group_rounds (group_id, round_key, created_at)
SELECT DISTINCT group_id, round_week, now()
FROM round_matches
ON CONFLICT (group_id, round_key) DO NOTHING;

INSERT INTO group_rounds (group_id, round_key, created_at)
SELECT DISTINCT group_id, round_week, now()
FROM round_results
ON CONFLICT (group_id, round_key) DO NOTHING;
