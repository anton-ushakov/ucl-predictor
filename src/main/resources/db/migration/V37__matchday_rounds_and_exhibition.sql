-- Туры перестают быть календарными неделями, и появляется участие вне конкурса.
--
-- Неделя была удобной подпоркой: тур = неделя, ключ тура = понедельник. Но играют не
-- неделями, а турами чемпионатов: тур Лиги чемпионов идёт со вторника по четверг, тур
-- Англии — с субботы по понедельник, и в одну неделю их попадает два разных. Поэтому тур
-- теперь знает, из чего он собран, и собирается сам по номеру тура турнира.
--
-- Ключ тура остаётся датой: на нём держатся прогнозы, итоги, долги и ссылки в оповещениях,
-- и переделывать это ради красоты было бы опаснее, чем оставить. Меняется смысл: это больше
-- не понедельник недели, а просто день, которым тур обозначен.

-- Номера команд в матче. Нужны, чтобы связать матч с таблицей его турнира: по названиям
-- это ненадёжно — «Man United» в матче и «Manchester United FC» в таблице.
ALTER TABLE matches ADD COLUMN home_team_id BIGINT;
ALTER TABLE matches ADD COLUMN away_team_id BIGINT;

-- Из чего собран тур: «CL:5» — пятый тур Лиги чемпионов, «TOP:14» — подборка топ-матчей
-- к четырнадцатому туру чемпионатов. По этому ключу автосостав находит свой тур второй раз
-- и дополняет его, а не создаёт рядом ещё один.
ALTER TABLE group_rounds ADD COLUMN source_key VARCHAR(40);
CREATE UNIQUE INDEX uq_group_rounds_source ON group_rounds (group_id, source_key)
    WHERE source_key IS NOT NULL;

-- Подборка топ-матчей пяти чемпионатов: включается в настройках группы.
ALTER TABLE friend_groups ADD COLUMN top_matches BOOLEAN NOT NULL DEFAULT FALSE;

-- Участие вне конкурса: баллы идут в общий зачёт сезона, а шаурму такой участник не
-- покупает и не получает. Нужно тем, кто в этот раз не хочет играть на шаурму, но и
-- выпадать из сезона не хочет.
CREATE TABLE round_exhibition (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_key  DATE        NOT NULL,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_round_exhibition UNIQUE (group_id, round_key, user_id)
);

CREATE INDEX idx_round_exhibition_round ON round_exhibition (group_id, round_key);
