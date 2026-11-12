-- ============================================================
-- V7: как сдаются прогнозы — весь тур сразу, по игровым дням или по одному матчу
-- ============================================================

ALTER TABLE friend_groups
    ADD COLUMN submission_mode VARCHAR(20) NOT NULL DEFAULT 'WHOLE_ROUND';

ALTER TABLE round_rules
    ADD COLUMN submission_mode VARCHAR(20) NOT NULL DEFAULT 'WHOLE_ROUND';
