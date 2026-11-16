package com.example.predictor.feed;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.standings.RoundWeeks;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Перевод ответа api-sports.io в то же самое представление, что и у football-data.org.
 * Дальше по коду разницы между источниками нет: матч есть матч.
 */
public final class ApiFootballMapper {

    private ApiFootballMapper() {
    }

    public static Optional<FeedMapper.Mapped> map(ApiFootballDtos.Item item, ZoneId zone) {
        if (item == null || item.fixture() == null || item.fixture().id() == null
                || item.teams() == null) {
            return Optional.empty();
        }
        String home = teamName(item.teams().home());
        String away = teamName(item.teams().away());
        if (home == null || away == null) {
            return Optional.empty();
        }
        Instant kickoff = ApiFootballClient.parseDate(item.fixture().date());
        if (kickoff == null) {
            return Optional.empty();
        }
        MatchStatus status = status(item.fixture().status());
        return Optional.of(new FeedMapper.Mapped(
                item.fixture().id(), home, away,
                logo(item.teams().home()), logo(item.teams().away()),
                kickoff, RoundWeeks.mondayOf(kickoff, zone),
                stage(item.league()), matchday(item.league()), status, parts(item, status)));
    }

    /** Состояния у источника свои: переводим их в наши четыре. */
    static MatchStatus status(ApiFootballDtos.Status status) {
        String code = status == null || status.code() == null ? "" : status.code().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE" -> MatchStatus.LIVE;
            case "FT", "AET", "PEN", "AWD", "WO" -> MatchStatus.FINISHED;
            case "PST" -> MatchStatus.POSTPONED;
            case "CANC", "ABD" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED;
        };
    }

    /**
     * Счёт по частям. У сыгранного матча основное время берём из fulltime, а голы добавочного
     * считаем как разницу с общим счётом: так ответ понимается одинаково, как бы источник
     * ни трактовал своё поле extratime.
     */
    static FeedMapper.Parts parts(ApiFootballDtos.Item item, MatchStatus status) {
        ApiFootballDtos.Goals goals = item.goals();
        ApiFootballDtos.Score score = item.score();

        if (status != MatchStatus.FINISHED) {
            // идёт или ещё не начался: показываем текущий счёт, в баллы он не идёт
            if (goals == null || goals.home() == null || goals.away() == null) {
                return null;
            }
            return new FeedMapper.Parts(goals.home(), goals.away(), null, null, null, null, "REGULAR");
        }

        Integer regularHome = null;
        Integer regularAway = null;
        if (score != null && score.fulltime() != null) {
            regularHome = score.fulltime().home();
            regularAway = score.fulltime().away();
        }
        if (regularHome == null || regularAway == null) {
            if (goals == null || goals.home() == null || goals.away() == null) {
                return null;
            }
            regularHome = goals.home();
            regularAway = goals.away();
        }

        boolean penalties = score != null && score.penalty() != null
                && score.penalty().home() != null && score.penalty().away() != null;
        boolean extra = penalties || (score != null && score.extratime() != null
                && score.extratime().home() != null && score.extratime().away() != null);

        Integer extraHome = null;
        Integer extraAway = null;
        if (extra && goals != null && goals.home() != null && goals.away() != null) {
            extraHome = Math.max(0, goals.home() - regularHome);
            extraAway = Math.max(0, goals.away() - regularAway);
        }
        Integer penHome = penalties ? score.penalty().home() : null;
        Integer penAway = penalties ? score.penalty().away() : null;
        String duration = penalties ? "PENALTY_SHOOTOUT" : extra ? "EXTRA_TIME" : "REGULAR";

        return new FeedMapper.Parts(regularHome, regularAway, extraHome, extraAway,
                penHome, penAway, duration);
    }

    /** «Group Stage - 5» → этап, если он осмысленный. */
    static String stage(ApiFootballDtos.League league) {
        if (league == null || league.round() == null || league.round().isBlank()) {
            return null;
        }
        String round = league.round().trim();
        int dash = round.lastIndexOf(" - ");
        String name = dash > 0 ? round.substring(0, dash) : round;
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    /** Номер тура из «Regular Season - 14». */
    static Integer matchday(ApiFootballDtos.League league) {
        if (league == null || league.round() == null) {
            return null;
        }
        int dash = league.round().lastIndexOf(" - ");
        if (dash < 0) {
            return null;
        }
        try {
            return Integer.valueOf(league.round().substring(dash + 3).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String teamName(ApiFootballDtos.Team team) {
        if (team == null || team.name() == null || team.name().isBlank()) {
            return null;
        }
        String name = team.name().trim();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    /** Эмблема. Берём только https: картинку по http телефон не покажет. */
    static String logo(ApiFootballDtos.Team team) {
        if (team == null || team.logo() == null) {
            return null;
        }
        String url = team.logo().trim();
        if (!url.startsWith("https://")) {
            return null;
        }
        return url.length() > 300 ? null : url;
    }
}
