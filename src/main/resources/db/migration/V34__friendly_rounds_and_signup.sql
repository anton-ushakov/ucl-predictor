-- Товарищеский тур и одобрение новых участников.
--
-- Товарищеский — это тур просто на победителя: таблица, майка лидера и общий зачёт как
-- обычно, но шаурмы никто никому не должен. Обычный тур остаётся обычным: по умолчанию
-- играют на шаурму, товарищеский выбирают сознательно.
ALTER TABLE group_rounds ADD COLUMN friendly BOOLEAN NOT NULL DEFAULT FALSE;

-- Новая учётная запись ждёт одобрения владельца приложения. Все, кто уже есть, одобрены
-- задним числом: их время входа и есть их одобрение.
ALTER TABLE app_users ADD COLUMN approved_at TIMESTAMPTZ;
UPDATE app_users SET approved_at = created_at WHERE approved_at IS NULL;
