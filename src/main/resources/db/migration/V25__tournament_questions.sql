-- ============================================================
-- V25: вопросы на турнир
-- ============================================================

-- Длинный прогноз: кто выиграет турнир, кто станет лучшим бомбардиром. Отвечают до закрытия,
-- после закрытия ответы видны всем, а правильные отмечает владелец группы.
CREATE TABLE tournament_questions (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT       NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    hint           VARCHAR(300),
    points         INTEGER      NOT NULL,
    closes_at      TIMESTAMPTZ  NOT NULL,
    round_week     DATE         NOT NULL,
    correct_answer VARCHAR(200),
    settled_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     BIGINT REFERENCES app_users (id)
);

CREATE INDEX idx_questions_group ON tournament_questions (group_id, closes_at);

-- Ответ участника. Пока приём открыт, его можно переписать.
CREATE TABLE question_answers (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT       NOT NULL REFERENCES tournament_questions (id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    answer      VARCHAR(200) NOT NULL,
    correct     BOOLEAN,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_question_answer UNIQUE (question_id, user_id)
);
