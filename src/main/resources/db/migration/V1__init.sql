CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE friend_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    invite_code VARCHAR(16)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    group_id BIGINT NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE matches (
    id          BIGSERIAL PRIMARY KEY,
    external_id BIGINT       NOT NULL UNIQUE,  -- id матча в football-data.org
    home_team   VARCHAR(100) NOT NULL,
    away_team   VARCHAR(100) NOT NULL,
    kickoff_at  TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL,         -- SCHEDULED, LIVE, FINISHED
    home_score  INTEGER,                        -- NULL, пока матч не закончен
    away_score  INTEGER
);

CREATE INDEX idx_matches_kickoff ON matches (kickoff_at);

CREATE TABLE predictions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    match_id   BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    home_goals INTEGER     NOT NULL CHECK (home_goals >= 0),
    away_goals INTEGER     NOT NULL CHECK (away_goals >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prediction_user_match UNIQUE (user_id, match_id)
);

CREATE INDEX idx_predictions_match ON predictions (match_id);
