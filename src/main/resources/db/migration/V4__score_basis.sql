-- ============================================================
-- V4: какой счёт считается в группе (основное время / + добавочное / + пенальти)
-- ============================================================

-- Матчи: счёт по частям. home_score/away_score остаются счётом основного времени (90 минут).
-- extra_*: голы только в добавочное время, pen_*: голы только в серии пенальти.
-- NULL означает, что такого периода в матче не было.
ALTER TABLE matches ADD COLUMN extra_home INTEGER;
ALTER TABLE matches ADD COLUMN extra_away INTEGER;
ALTER TABLE matches ADD COLUMN pen_home   INTEGER;
ALTER TABLE matches ADD COLUMN pen_away   INTEGER;

-- Группы: настройки по умолчанию. По умолчанию считаем только основное время.
ALTER TABLE friend_groups ADD COLUMN count_extra_time BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE friend_groups ADD COLUMN count_penalties  BOOLEAN NOT NULL DEFAULT FALSE;

-- Правила тура, зафиксированные при первом сохранённом прогнозе.
ALTER TABLE round_rules ADD COLUMN count_extra_time BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE round_rules ADD COLUMN count_penalties  BOOLEAN NOT NULL DEFAULT FALSE;

-- История: по какому счёту были подведены итоги тура.
ALTER TABLE round_results ADD COLUMN count_extra_time BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE round_results ADD COLUMN count_penalties  BOOLEAN NOT NULL DEFAULT FALSE;
