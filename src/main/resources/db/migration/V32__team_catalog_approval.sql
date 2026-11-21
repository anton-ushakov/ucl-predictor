-- Справочник команд — общий на всё приложение, а меняют его из разных групп.
--
-- Поэтому новая команда появляется сразу (о ней приходит оповещение владельцу приложения),
-- а переименование и удаление существующей сначала становятся заявкой: пока её не одобрят,
-- в справочнике всё остаётся как было. Так один человек не переименует «Спартак» так, что
-- это увидят все остальные группы.
CREATE TABLE team_change_requests
(
    id           BIGSERIAL PRIMARY KEY,
    team_id      BIGINT      NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    author_id    BIGINT      NOT NULL REFERENCES app_users (id),
    kind         VARCHAR(16) NOT NULL,
    name         VARCHAR(100),
    crest        VARCHAR(300),
    country_code VARCHAR(8),
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    decided_at   TIMESTAMPTZ,
    decided_by   BIGINT REFERENCES app_users (id)
);

CREATE INDEX idx_team_changes_status ON team_change_requests (status, id);

-- Оповещение о справочнике к группе не относится: группы у него нет.
ALTER TABLE notifications ALTER COLUMN group_id DROP NOT NULL;
