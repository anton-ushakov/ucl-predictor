#!/bin/sh
# ============================================================
# Запрос к живой базе — той, с которой работает сервер.
#
#   ./sql.sh                          — открыть psql и спрашивать руками
#   ./sql.sh "SELECT ..."             — выполнить один запрос
#   ./sql.sh --itogi                  — итоги туров: баллы, показатели, кто купил шаурму
#   ./sql.sh --pravila                — какие правила заморожены у каждого тура
#   ./sql.sh --matchi                 — матчи за сутки: статус, счёт и когда их трогали
#
# Зачем скрипт: порт базы наружу не открыт — её видит только приложение, —
# поэтому pgAdmin с ноутбука подключается не к ней, а к своей, местной. Старые
# данные с этапа проверки лежат как раз там. Здесь же psql запускается внутри
# контейнера, и видно именно то, с чем работает сервер.
# ============================================================

set -e
cd "$(dirname "$0")"

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

ITOGI='SELECT g.name AS "группа", r.round_week AS "тур", u.username AS "кто",
       r.place AS "место", r.points AS "баллы", r.exact_scores AS "точных",
       r.goal_diff_hits AS "разниц", r.goal_error AS "ошибка",
       r.submitted_at AS "сохранил", r.winner AS "выиграл", r.loser AS "купил",
       r.tiebreakers AS "показатели тура"
FROM round_results r
JOIN app_users u ON u.id = r.user_id
JOIN friend_groups g ON g.id = r.group_id
ORDER BY r.round_week DESC, g.name, r.place;'

PRAVILA='SELECT g.name AS "группа", r.round_week AS "тур", r.tiebreakers AS "показатели",
       r.lock_minutes_before AS "закрытие за, мин", r.frozen_at AS "заморожены"
FROM round_rules r
JOIN friend_groups g ON g.id = r.group_id
ORDER BY r.round_week DESC;'

# «Показатели сейчас» рядом с замороженными: видно, что настройки меняли после начала тура
SEYCHAS='SELECT name AS "группа", tiebreakers AS "показатели сейчас"
FROM friend_groups ORDER BY id;'

# Счёт не появился — смотреть надо сюда: что сервер успел записать и из какого источника
MATCHI='SELECT m.kickoff_at AS "начало", c.short_name AS "турнир",
       m.home_team AS "хозяева", m.away_team AS "гости", m.status AS "статус",
       m.home_score AS "х", m.away_score AS "г",
       m.live_home AS "х сейчас", m.live_away AS "г сейчас", m.source AS "источник"
FROM matches m
LEFT JOIN competitions c ON c.id = m.competition_id
-- make_interval вместо interval: одинарные кавычки внутри такой строки sh не сохраняет
WHERE m.kickoff_at > now() - make_interval(hours => 30)
  AND m.kickoff_at < now() + make_interval(hours => 6)
ORDER BY m.kickoff_at;'

QUERY2=""
case "$1" in
    --itogi)   QUERY="$ITOGI" ;;
    --matchi)  QUERY="$MATCHI" ;;
    --pravila) QUERY="$PRAVILA"; QUERY2="$SEYCHAS" ;;
    "")        QUERY="" ;;
    *)         QUERY="$1" ;;
esac

if [ -z "$QUERY" ]; then
    # руками: выход — \q
    docker compose -f "$FILE" exec db psql -U predictor -d ucl_predictor
    exit 0
fi

# каждый запрос своим -c: с несколькими командами в одной строке psql печатает только последнюю
if [ -n "$QUERY2" ]; then
    docker compose -f "$FILE" exec -T db \
        psql -U predictor -d ucl_predictor -P pager=off -c "$QUERY" -c "$QUERY2"
else
    docker compose -f "$FILE" exec -T db \
        psql -U predictor -d ucl_predictor -P pager=off -c "$QUERY"
fi
