-- ============================================================
-- V18: адреса устройств для мгновенных оповещений
--
-- Firebase присылает приложению адрес устройства, приложение передаёт его
-- сюда, а сервер шлёт по этим адресам push. У одного участника может быть
-- несколько телефонов, поэтому записей на человека бывает больше одной.
-- ============================================================

CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token      VARCHAR(500) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL,
    seen_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
