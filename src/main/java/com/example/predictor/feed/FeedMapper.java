package com.example.predictor.feed;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.standings.RoundWeeks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/** Перевод матча из формата football-data.org в наш. Чистая логика, без базы и сети. */
public final class FeedMapper {

    /**
     * Счёт матча по частям. extra*: голы только в добавочное время, pen*: голы только в серии пенальти;
     * null, если такого периода не было.
     */
    public record Parts(int homeScore, int awayScore, Integer extraHome, Integer extraAway,
                        Integer penHome, Integer penAway, String duration) {
    }

    public record Mapped(long externalId, String homeTeam, String awayTeam, Instant kickoffAt,
                         LocalDate roundWeek, String stage, Integer matchday, MatchStatus status,
                         Parts score) {
    }

    private FeedMapper() {
    }

    /**
     * Пустой результат, если матч нельзя использовать: нет времени или команды ещё не определены
     * (так бывает в плей-офф до жеребьёвки).
     */
    public static Optional<Mapped> map(FeedDtos.Match m, ZoneId zone) {
        if (m == null || m.id() == null || m.utcDate() == null) {
            return Optional.empty();
        }
        String home = teamName(m.homeTeam());
        String away = teamName(m.awayTeam());
        if (home == null || away == null) {
            return Optional.empty();
        }
        Instant kickoff;
        try {
            kickoff = Instant.parse(m.utcDate());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        return Optional.of(new Mapped(
                m.id(), home, away, kickoff, RoundWeeks.mondayOf(kickoff, zone),
                truncate(m.stage(), 40), m.matchday(), status(m.status()), parts(m.score())));
    }

    static MatchStatus status(String raw) {
        if (raw == null) {
            return MatchStatus.SCHEDULED;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "IN_PLAY", "PAUSED", "LIVE", "SUSPENDED" -> MatchStatus.LIVE;
            case "FINISHED", "AWARDED" -> MatchStatus.FINISHED;
            case "POSTPONED" -> MatchStatus.POSTPONED;
            case "CANCELLED" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED; // SCHEDULED, TIMED
        };
    }

    static String teamName(FeedDtos.Team team) {
        if (team == null) {
            return null;
        }
        if (team.shortName() != null && !team.shortName().isBlank()) {
            return truncate(team.shortName().trim(), 100);
        }
        if (team.name() != null && !team.name().isBlank()) {
            return truncate(team.name().trim(), 100);
        }
        return null;
    }

    /**
     * Счёт матча по частям. Общий счёт football-data.org (fullTime) при добавочном времени и пенальти
     * уже включает их, поэтому берём отдельные поля. Если известен счёт основного времени, а данных о
     * добавочном времени или пенальти ещё нет, соответствующие части остаются null, а duration показывает,
     * что такой период был: группа сама решает, что делать с таким матчем (см. MatchResolution).
     * Без счёта основного времени возвращаем null: матч не считается сыгранным.
     */
    static Parts parts(FeedDtos.Score s) {
        if (s == null) {
            return null;
        }
        String duration = s.duration() == null ? "REGULAR" : s.duration().toUpperCase(Locale.ROOT);
        boolean extra = duration.equals("EXTRA_TIME") || duration.equals("PENALTY_SHOOTOUT");
        boolean penalties = duration.equals("PENALTY_SHOOTOUT");

        if (!extra) {
            if (!complete(s.fullTime())) {
                return null;
            }
            return new Parts(s.fullTime().home(), s.fullTime().away(), null, null, null, null, "REGULAR");
        }

        FeedDtos.Goals regular = s.regularTime();
        if (!complete(regular)) {
            return null;
        }
        Integer extraHome = null;
        Integer extraAway = null;
        if (complete(s.extraTime())) {
            extraHome = s.extraTime().home();
            extraAway = s.extraTime().away();
        } else if (!penalties && complete(s.fullTime())) {
            // добавочное время без пенальти: общий счёт = основное + добавочное
            int h = s.fullTime().home() - regular.home();
            int a = s.fullTime().away() - regular.away();
            if (h >= 0 && a >= 0) {
                extraHome = h;
                extraAway = a;
            }
        }
        Integer penHome = null;
        Integer penAway = null;
        if (penalties && complete(s.penalties())) {
            penHome = s.penalties().home();
            penAway = s.penalties().away();
        }
        return new Parts(regular.home(), regular.away(), extraHome, extraAway, penHome, penAway, duration);
    }

    private static boolean complete(FeedDtos.Goals g) {
        return g != null && g.home() != null && g.away() != null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
