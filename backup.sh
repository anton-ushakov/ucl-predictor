#!/bin/sh
# ============================================================
# Резервная копия базы.
#
#   ./backup.sh            — сделать копию
#   ./backup.sh --list     — показать, что уже сохранено
#
# Копии лежат в папке backups рядом с проектом, хранятся последние 14.
# Самая свежая всегда доступна как backups/latest.sql.gz — её удобно
# забирать на другой компьютер.
#
# Каждую ночь это делается само, если добавить строку в crontab (см. ДОМА.md).
# ============================================================

set -e
cd "$(dirname "$0")"

KEEP=14
DIR=backups

# Какой compose-файл сейчас работает: домашний ноутбук или арендованный сервер
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

if [ "$1" = "--list" ]; then
    ls -lh "$DIR"/ucl-*.sql.gz 2>/dev/null || echo "Копий пока нет"
    exit 0
fi

mkdir -p "$DIR"
name="$DIR/ucl-$(date +%Y-%m-%d-%H%M).sql.gz"

# Пишем во временный файл: неудачную копию нельзя класть вместо удачной
tmp="$DIR/.in-progress.sql.gz"
docker compose -f "$FILE" exec -T db pg_dump -U predictor ucl_predictor | gzip > "$tmp"

# Пустой или обрезанный дамп — это не копия, а ложное спокойствие
size=$(wc -c < "$tmp")
if [ "$size" -lt 1000 ]; then
    rm -f "$tmp"
    echo "Копия не сделана: база ответила пустым дампом ($size байт)." >&2
    echo "Проверьте, работает ли контейнер db:  docker compose -f $FILE ps" >&2
    exit 1
fi

mv "$tmp" "$name"
cp "$name" "$DIR/latest.sql.gz"

# Храним только последние KEEP копий, остальные удаляем
ls -1t "$DIR"/ucl-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm --

count=$(ls -1 "$DIR"/ucl-*.sql.gz 2>/dev/null | wc -l)
echo "Копия сохранена: $name ($(du -h "$name" | cut -f1)), всего копий: $count"
