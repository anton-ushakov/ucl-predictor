-- ============================================================
-- V22: кто собирает состав тура
-- ============================================================

-- OWNER: только владелец группы (как было),
-- HELPERS: владелец и отмеченные им помощники,
-- ANYONE: любой участник группы.
ALTER TABLE friend_groups ADD COLUMN match_selection VARCHAR(10) NOT NULL DEFAULT 'OWNER';

-- Помощник может собирать тур, когда владельца нет рядом.
ALTER TABLE group_members ADD COLUMN helper BOOLEAN NOT NULL DEFAULT FALSE;
