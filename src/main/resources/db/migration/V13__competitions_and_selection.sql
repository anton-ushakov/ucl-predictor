-- ============================================================
-- V13: несколько турниров, отбор матчей владельцем и ручные матчи
--
-- Раньше тур был календарной неделей, в которую автоматически попадали все матчи
-- Лиги чемпионов. Теперь матчи приходят из разных турниров, а владелец группы
-- сам отбирает те, на которые делаются прогнозы.
-- ============================================================

-- Справочник турниров. code — обозначение в football-data.org, у ручных турниров его нет.
CREATE TABLE competitions (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(10) UNIQUE,
    name       VARCHAR(100) NOT NULL,
    short_name VARCHAR(40)  NOT NULL,
    -- матчи этого турнира загружаются автоматически
    synced     BOOLEAN      NOT NULL DEFAULT TRUE,
    -- выключенный турнир не загружается и не предлагается при отборе
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INTEGER      NOT NULL DEFAULT 100
);

INSERT INTO competitions (code, name, short_name, synced, enabled, sort_order) VALUES
    ('CL',  'Лига чемпионов УЕФА',        'Лига чемпионов', TRUE,  TRUE,  10),
    ('PL',  'Премьер-лига, Англия',       'Англия',         TRUE,  TRUE,  20),
    ('PD',  'Ла Лига, Испания',           'Испания',        TRUE,  TRUE,  30),
    ('SA',  'Серия А, Италия',            'Италия',         TRUE,  TRUE,  40),
    ('BL1', 'Бундеслига, Германия',       'Германия',       TRUE,  TRUE,  50),
    ('FL1', 'Лига 1, Франция',            'Франция',        TRUE,  TRUE,  60),
    ('PPL', 'Примейра, Португалия',       'Португалия',     TRUE,  TRUE,  70),
    ('DED', 'Эредивизи, Нидерланды',      'Нидерланды',     TRUE,  FALSE, 80),
    ('ELC', 'Чемпионшип, Англия',         'Чемпионшип',     TRUE,  FALSE, 90),
    ('BSA', 'Серия А, Бразилия',          'Бразилия',       TRUE,  FALSE, 100),
    ('WC',  'Чемпионат мира',             'ЧМ',             TRUE,  TRUE,  110),
    ('EC',  'Чемпионат Европы',           'ЧЕ',             TRUE,  TRUE,  120),
    -- турниры, которых нет в бесплатном тарифе: матчи добавляются вручную
    (NULL,  'Лига Европы УЕФА',           'Лига Европы',    FALSE, TRUE,  15),
    (NULL,  'Российская Премьер-лига',    'Россия',         FALSE, TRUE,  25),
    (NULL,  'Сборные: отбор и товарищеские', 'Сборные',     FALSE, TRUE,  115),
    (NULL,  'Другое',                     'Другое',         FALSE, TRUE,  200);

ALTER TABLE matches ADD COLUMN competition_id BIGINT REFERENCES competitions (id);

-- Матч, добавленный владельцем группы руками. Виден только его группе.
ALTER TABLE matches ADD COLUMN owner_group_id BIGINT REFERENCES friend_groups (id) ON DELETE CASCADE;
ALTER TABLE matches ADD COLUMN created_by     BIGINT REFERENCES app_users (id);

-- Все матчи, что уже есть, относятся к Лиге чемпионов
UPDATE matches SET competition_id = (SELECT id FROM competitions WHERE code = 'CL')
 WHERE competition_id IS NULL;

-- external_id есть только у загруженных матчей, у ручных его нет
ALTER TABLE matches ALTER COLUMN external_id DROP NOT NULL;

CREATE INDEX idx_matches_competition ON matches (competition_id, round_week);

-- Матчи, которые владелец отобрал в тур своей группы
CREATE TABLE round_matches (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    round_week DATE        NOT NULL,
    match_id   BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    added_by   BIGINT      NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_round_match UNIQUE (group_id, match_id)
);

CREATE INDEX idx_round_matches_round ON round_matches (group_id, round_week);

-- Прежние группы играли по Лиге чемпионов: переносим их туры в новый вид,
-- чтобы уже сделанные прогнозы остались в силе.
INSERT INTO round_matches (group_id, round_week, match_id, added_by, created_at)
SELECT DISTINCT p.group_id, m.round_week, m.id,
       (SELECT gm.user_id FROM group_members gm
         WHERE gm.group_id = p.group_id AND gm.role = 'OWNER' LIMIT 1),
       now()
  FROM predictions p
  JOIN matches m ON m.id = p.match_id
 WHERE EXISTS (SELECT 1 FROM group_members gm
                WHERE gm.group_id = p.group_id AND gm.role = 'OWNER');
