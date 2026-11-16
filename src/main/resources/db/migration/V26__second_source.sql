-- ============================================================
-- V26: второй источник матчей (api-sports.io)
-- ============================================================

-- Откуда приехал матч. У ручных матчей источника нет.
ALTER TABLE matches ADD COLUMN source VARCHAR(20);
UPDATE matches SET source = 'FOOTBALL_DATA' WHERE external_id IS NOT NULL;

-- Номера матчей у разных источников свои и могут совпасть, поэтому
-- уникальность теперь в паре с источником.
ALTER TABLE matches DROP CONSTRAINT IF EXISTS matches_external_id_key;
CREATE UNIQUE INDEX uq_match_source_external ON matches (source, external_id);

-- Турнир тоже знает, откуда брать матчи. external_id — номер турнира в этом источнике.
ALTER TABLE competitions ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'FOOTBALL_DATA';
ALTER TABLE competitions ADD COLUMN external_id INTEGER;

-- Турниры, которые заполнялись руками, так и остаются ручными
UPDATE competitions SET source = 'MANUAL' WHERE code IS NULL;

-- Лига Европы и чемпионат России есть в api-sports.io: теперь они загружаются сами.
-- Если номер турнира окажется другим, его видно в логе и меняется одной строкой.
UPDATE competitions SET source = 'API_FOOTBALL', external_id = 3, synced = TRUE
 WHERE name = 'Лига Европы УЕФА';
UPDATE competitions SET source = 'API_FOOTBALL', external_id = 235, synced = TRUE
 WHERE name = 'Российская Премьер-лига';

-- Турниры, которых не было совсем
INSERT INTO competitions (code, name, short_name, synced, enabled, sort_order, source, external_id)
VALUES
    (NULL, 'Лига конференций УЕФА', 'Лига конференций', TRUE, TRUE, 17, 'API_FOOTBALL', 848),
    (NULL, 'Лига наций УЕФА',       'Лига наций',       TRUE, TRUE, 113, 'API_FOOTBALL', 5);
