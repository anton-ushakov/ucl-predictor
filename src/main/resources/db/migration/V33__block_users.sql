-- Закрытый доступ.
--
-- Бывает, что человеку в приложении больше не место: он не должен ни войти, ни завести
-- группу, ни получать оповещения. Запись о нём остаётся — прогнозы, итоги и долги прошлых
-- туров никуда не деваются, — но вход для него закрыт, пока владелец приложения не откроет.
ALTER TABLE app_users ADD COLUMN blocked_at TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN blocked_by BIGINT REFERENCES app_users (id);
ALTER TABLE app_users ADD COLUMN block_reason VARCHAR(200);
