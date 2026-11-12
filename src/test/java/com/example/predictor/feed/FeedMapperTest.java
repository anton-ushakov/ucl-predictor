package com.example.predictor.feed;

import com.example.predictor.domain.MatchStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FeedMapperTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static FeedDtos.Match match(String status, String duration,
                                        FeedDtos.Goals full, FeedDtos.Goals regular) {
        return match(status, duration, full, regular, null, null);
    }

    private static FeedDtos.Match match(String status, String duration, FeedDtos.Goals full,
                                        FeedDtos.Goals regular, FeedDtos.Goals extra, FeedDtos.Goals pens) {
        return new FeedDtos.Match(501L, "2026-11-04T20:00:00Z", status, "LEAGUE_STAGE", 4,
                new FeedDtos.Team("Real Madrid CF", "Real Madrid"),
                new FeedDtos.Team("Juventus FC", "Juventus"),
                new FeedDtos.Score(duration, full, regular, extra, pens));
    }

    @Test
    void scheduledMatchHasNoScore() {
        FeedMapper.Mapped m = FeedMapper.map(
                match("TIMED", "REGULAR", new FeedDtos.Goals(null, null), null), MOSCOW).orElseThrow();
        assertEquals(MatchStatus.SCHEDULED, m.status());
        assertNull(m.score());
        assertEquals("Real Madrid", m.homeTeam());
        assertEquals("Juventus", m.awayTeam());
    }

    @Test
    void finishedRegularMatchUsesFullTime() {
        FeedMapper.Mapped m = FeedMapper.map(
                match("FINISHED", "REGULAR", new FeedDtos.Goals(3, 1), null), MOSCOW).orElseThrow();
        assertEquals(MatchStatus.FINISHED, m.status());
        assertEquals(3, m.score().homeScore());
        assertEquals(1, m.score().awayScore());
    }

    @Test
    void penaltyShootoutKeepsAllPartsSeparately() {
        // fullTime включает и добавочное время, и серию; храним части отдельно
        FeedMapper.Mapped m = FeedMapper.map(match("FINISHED", "PENALTY_SHOOTOUT",
                new FeedDtos.Goals(7, 6), new FeedDtos.Goals(1, 1),
                new FeedDtos.Goals(1, 1), new FeedDtos.Goals(4, 3)), MOSCOW).orElseThrow();
        FeedMapper.Parts p = m.score();
        assertEquals(1, p.homeScore());
        assertEquals(1, p.awayScore());
        assertEquals(1, p.extraHome());
        assertEquals(1, p.extraAway());
        assertEquals(4, p.penHome());
        assertEquals(3, p.penAway());
    }

    @Test
    void extraTimeWithoutExtraFieldIsDerivedFromFullTime() {
        FeedMapper.Parts p = FeedMapper.map(match("FINISHED", "EXTRA_TIME",
                new FeedDtos.Goals(3, 2), new FeedDtos.Goals(2, 2)), MOSCOW).orElseThrow().score();
        assertEquals(2, p.homeScore());
        assertEquals(2, p.awayScore());
        assertEquals(1, p.extraHome());
        assertEquals(0, p.extraAway());
        assertNull(p.penHome());
    }

    @Test
    void regularMatchHasNoExtraOrPenaltyParts() {
        FeedMapper.Parts p = FeedMapper.map(
                match("FINISHED", "REGULAR", new FeedDtos.Goals(2, 0), null), MOSCOW).orElseThrow().score();
        assertNull(p.extraHome());
        assertNull(p.penHome());
    }

    @Test
    void shootoutWithoutPenaltyDataKeepsRegularScoreButNoPenaltyPart() {
        FeedMapper.Parts p = FeedMapper.map(match("FINISHED", "PENALTY_SHOOTOUT",
                new FeedDtos.Goals(7, 6), new FeedDtos.Goals(1, 1),
                new FeedDtos.Goals(0, 0), null), MOSCOW).orElseThrow().score();
        assertEquals(1, p.homeScore());
        assertEquals(0, p.extraHome());
        assertNull(p.penHome());
        assertEquals("PENALTY_SHOOTOUT", p.duration());
    }

    @Test
    void shootoutWithoutExtraTimeDataLeavesExtraUnknown() {
        FeedMapper.Parts p = FeedMapper.map(match("FINISHED", "PENALTY_SHOOTOUT",
                new FeedDtos.Goals(7, 6), new FeedDtos.Goals(1, 1),
                null, new FeedDtos.Goals(4, 3)), MOSCOW).orElseThrow().score();
        assertNull(p.extraHome());
        assertEquals(4, p.penHome());
    }

    @Test
    void overtimeWithoutRegularTimeScoreGivesNoScore() {
        assertNull(FeedMapper.map(match("FINISHED", "EXTRA_TIME",
                new FeedDtos.Goals(3, 2), null), MOSCOW).orElseThrow().score());
    }

    @Test
    void statusesAreMapped() {
        assertEquals(MatchStatus.SCHEDULED, FeedMapper.status("SCHEDULED"));
        assertEquals(MatchStatus.SCHEDULED, FeedMapper.status("TIMED"));
        assertEquals(MatchStatus.LIVE, FeedMapper.status("IN_PLAY"));
        assertEquals(MatchStatus.LIVE, FeedMapper.status("PAUSED"));
        assertEquals(MatchStatus.FINISHED, FeedMapper.status("FINISHED"));
        assertEquals(MatchStatus.POSTPONED, FeedMapper.status("POSTPONED"));
        assertEquals(MatchStatus.CANCELLED, FeedMapper.status("CANCELLED"));
        assertEquals(MatchStatus.SCHEDULED, FeedMapper.status(null));
    }

    @Test
    void roundWeekIsMondayInMoscow() {
        // 2026-11-04 (среда), 20:00 UTC = 23:00 по Москве: неделя с понедельника 2026-11-02
        FeedMapper.Mapped m = FeedMapper.map(
                match("TIMED", "REGULAR", new FeedDtos.Goals(null, null), null), MOSCOW).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 2), m.roundWeek());
    }

    @Test
    void lateSundayInUtcCanBeNextWeekInMoscow() {
        // воскресенье 2026-11-08, 22:30 UTC = понедельник 01:30 по Москве
        FeedDtos.Match raw = new FeedDtos.Match(502L, "2026-11-08T22:30:00Z", "TIMED", "LEAGUE_STAGE", 4,
                new FeedDtos.Team("A", "A"), new FeedDtos.Team("B", "B"), null);
        FeedMapper.Mapped m = FeedMapper.map(raw, MOSCOW).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 9), m.roundWeek());
    }

    @Test
    void matchWithoutTeamsOrTimeIsSkipped() {
        FeedDtos.Match noTeams = new FeedDtos.Match(503L, "2026-11-04T20:00:00Z", "TIMED", "PLAYOFFS", null,
                new FeedDtos.Team(null, null), new FeedDtos.Team("B", "B"), null);
        assertEquals(Optional.empty(), FeedMapper.map(noTeams, MOSCOW));

        FeedDtos.Match noTime = new FeedDtos.Match(504L, null, "TIMED", "PLAYOFFS", null,
                new FeedDtos.Team("A", "A"), new FeedDtos.Team("B", "B"), null);
        assertEquals(Optional.empty(), FeedMapper.map(noTime, MOSCOW));

        FeedDtos.Match badTime = new FeedDtos.Match(505L, "вчера", "TIMED", "PLAYOFFS", null,
                new FeedDtos.Team("A", "A"), new FeedDtos.Team("B", "B"), null);
        assertEquals(Optional.empty(), FeedMapper.map(badTime, MOSCOW));
    }

    @Test
    void shortNameIsPreferredButFullNameIsFallback() {
        assertEquals("Real Madrid", FeedMapper.teamName(new FeedDtos.Team("Real Madrid CF", "Real Madrid")));
        assertEquals("Real Madrid CF", FeedMapper.teamName(new FeedDtos.Team("Real Madrid CF", " ")));
        assertNull(FeedMapper.teamName(null));
    }
}
