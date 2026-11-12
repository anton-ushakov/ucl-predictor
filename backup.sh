#!/bin/sh
# Резервная копия базы. Запуск: ./backup.sh
# Хранит последние 14 копий в папке backups рядом с проектом.

set -e
cd "$(dirname "$0")"
mkdir -p backups

name="backups/ucl-$(date +%Y-%m-%d-%H%M).sql.gz"
docker compose exec -T db pg_dump -U predictor ucl_predictor | gzip > "$name"
echo "Копия сохранена: $name"

# Удаляем всё, кроме 14 самых свежих копий
ls -1t backups/ucl-*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm --
