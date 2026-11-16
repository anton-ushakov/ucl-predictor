#!/bin/sh
# ============================================================
# Восстановление базы из резервной копии.
#
#   ./restore.sh                        — из самой свежей копии
#   ./restore.sh backups/ucl-....sql.gz — из указанной
#
# Что происходит: приложение останавливается, текущая база стирается,
# на её место разворачивается копия, приложение запускается обратно.
# Всё, что появилось после копии, пропадёт — поэтому скрипт спрашивает
# подтверждение и сначала делает копию того, что есть сейчас.
# ============================================================

set -e
cd "$(dirname "$0")"

compose_file() {
    if [ -n "$COMPOSE" ]; then
        echo "$COMPOSE"
        return
    fi
    if docker compose -f docker-compose.home.yml ps --status running --services 2>/dev/null \
        | grep -q '^db$'; then
        echo docker-compose.home.yml
    else
        echo docker-compose.yml
    fi
}

FILE=$(compose_file)
SOURCE=${1:-backups/latest.sql.gz}

if [ ! -f "$SOURCE" ]; then
    echo "Нет такого файла: $SOURCE" >&2
    echo "Что есть:" >&2
    ls -1 backups/ucl-*.sql.gz 2>/dev/null >&2 || echo "  копий нет" >&2
    exit 1
fi

echo "Восстановить базу из $SOURCE?"
echo "Текущие прогнозы, туры и долги будут заменены на то, что в копии."
printf "Напишите да и нажмите ввод: "
read -r answer
if [ "$answer" != "да" ]; then
    echo "Отменено, ничего не тронуто."
    exit 0
fi

echo "== Сохраняем то, что есть сейчас =="
./backup.sh || echo "Копию сделать не удалось, продолжаем по вашему решению"

echo "== Останавливаем приложение =="
docker compose -f "$FILE" stop app

echo "== Очищаем базу =="
docker compose -f "$FILE" exec -T db psql -U predictor -d ucl_predictor \
    -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'

echo "== Разворачиваем копию =="
gunzip -c "$SOURCE" | docker compose -f "$FILE" exec -T db psql -U predictor -d ucl_predictor > /dev/null

echo "== Запускаем приложение =="
docker compose -f "$FILE" start app

echo
echo "Готово. Проверьте логи:  docker compose -f $FILE logs -f app"
