#!/bin/sh
# ============================================================
# Обновление сервера: забрать новый код и пересобрать.
# Запуск из папки проекта:  ./update.sh
# ============================================================
set -e
cd "$(dirname "$0")"

echo "== Забираем изменения =="
git pull

echo
echo "== Пересобираем и запускаем =="
docker compose -f docker-compose.home.yml up -d --build

echo
echo "== Что работает =="
docker compose -f docker-compose.home.yml ps

echo
echo "Готово. Логи приложения:  docker compose -f docker-compose.home.yml logs -f app"
