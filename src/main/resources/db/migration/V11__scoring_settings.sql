-- ============================================================
-- V11: начисление баллов настраивается в группе
--
-- Значения по умолчанию повторяют прежние правила: точный счёт 3 балла,
-- угаданный исход 1 балл. Баллы за угаданную разницу мячей по умолчанию
-- не начисляются, но их можно включить.
-- ============================================================

ALTER TABLE friend_groups ADD COLUMN exact_score_points INTEGER NOT NULL DEFAULT 3;
ALTER TABLE friend_groups ADD COLUMN goal_diff_points   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE friend_groups ADD COLUMN outcome_points     INTEGER NOT NULL DEFAULT 1;

-- Те же значения замораживаются на тур, чтобы изменение настроек не переписало прошлое
ALTER TABLE round_rules ADD COLUMN exact_score_points INTEGER NOT NULL DEFAULT 3;
ALTER TABLE round_rules ADD COLUMN goal_diff_points   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE round_rules ADD COLUMN outcome_points     INTEGER NOT NULL DEFAULT 1;
