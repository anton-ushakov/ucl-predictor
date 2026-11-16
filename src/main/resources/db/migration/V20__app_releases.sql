-- ============================================================
-- V20: выложенные версии приложения
-- ============================================================

-- Сам файл apk лежит на диске сервера (папка из переменной RELEASES_DIR),
-- в базе — только сведения о нём. Версия с наибольшим version_code считается последней.
CREATE TABLE app_releases (
    id           BIGSERIAL PRIMARY KEY,
    version_code INTEGER      NOT NULL,
    version_name VARCHAR(40)  NOT NULL,
    note         VARCHAR(400),
    file_name    VARCHAR(120) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   BIGINT REFERENCES app_users (id),
    CONSTRAINT uq_app_release UNIQUE (version_code)
);
