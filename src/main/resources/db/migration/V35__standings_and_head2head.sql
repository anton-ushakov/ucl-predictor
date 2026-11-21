-- Что ещё можно взять у источника: турнирная таблица и личные встречи.
--
-- Таблица обновляется раз в час одним запросом на турнир и заодно приносит форму команд —
-- строку вида «W,W,D,L,W». Личные встречи спрашиваются по одному матчу и редко, потому что
-- бесплатный ключ позволяет десять запросов в минуту.
CREATE TABLE competition_standings
(
    id               BIGSERIAL PRIMARY KEY,
    competition_id   BIGINT       NOT NULL REFERENCES competitions (id) ON DELETE CASCADE,
    group_label      VARCHAR(60),
    position         INT          NOT NULL,
    team_external_id BIGINT,
    team_name        VARCHAR(100) NOT NULL,
    crest            VARCHAR(300),
    played           INT          NOT NULL,
    won              INT          NOT NULL,
    draw             INT          NOT NULL,
    lost             INT          NOT NULL,
    goals_for        INT          NOT NULL,
    goals_against    INT          NOT NULL,
    points           INT          NOT NULL,
    form             VARCHAR(40),
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_standings_competition ON competition_standings (competition_id, position);

CREATE TABLE match_head2head
(
    id          BIGSERIAL PRIMARY KEY,
    match_id    BIGINT      NOT NULL UNIQUE REFERENCES matches (id) ON DELETE CASCADE,
    total       INT         NOT NULL,
    home_wins   INT         NOT NULL,
    draws       INT         NOT NULL,
    away_wins   INT         NOT NULL,
    total_goals INT         NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
