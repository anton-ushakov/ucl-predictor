package com.example.predictor.feed;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.feed.ApiFootballDtos.Fixture;
import com.example.predictor.feed.ApiFootballDtos.Goals;
import com.example.predictor.feed.ApiFootballDtos.Item;
import com.example.predictor.feed.ApiFootballDtos.League;
import com.example.predictor.feed.ApiFootballDtos.Score;
import com.example.predictor.feed.ApiFootballDtos.Status;
import com.example.predictor.feed.ApiFootballDtos.Team;
import com.example.predictor.feed.ApiFootballDtos.Teams;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Перевод ответа api-sports.io в наше представление матча. */
class ApiFootballMapperTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static Item item(String statusCode, Goals goals, Score score, String round) {
        return new Item(
                new Fixture(777L, "2026-11-19T19:00:00+00:00", new Status(statusCode, null)),
                new League(3, "UEFA Europa League", round),
                new Teams(new Team(1, "Roma", "https://media.api-sports.io/1.png"),
                        new Team(2, "Porto", "http://media.api-sports.io/2.png")),
                goals, score);
    }

    @Test
    void scheduledMatchHasNoScore() {
        FeedMapper.Mapped m = ApiFootballMapper
                .map(item("NS", new Goals(null, null), null, "Group Stage - 5"), MOSCOW)
                .orElseThrow();

        assertEquals(MatchStatus.SCHEDULED, m.status());
        assertNull(m.score());
        assertEquals("Roma", m.homeTeam());
        // эмблема по http не берётся: телефон её не покажет
        assertNull(m.awayCrest());
        assertEquals(LocalDate.of(2026, 11, 16), m.roundWeek());
        assertEquals("Group Stage", m.stage());
        assertEquals(5, m.matchday());
    }

    @Test
    void liveMatchKeepsCurrentScoreOutOfTheResult() {
        FeedMapper.Mapped m = ApiFootballMapper
                .map(item("2H", new Goals(1, 0), new Score(null, null, null, null), null), MOSCOW)
                .orElseThrow();

        assertEquals(MatchStatus.LIVE, m.status());
        assertEquals(1, m.score().homeScore());
        assertEquals("REGULAR", m.score().duration());
    }

    @Test
    void extraTimeGoalsAreTheDifferenceFromNinetyMinutes() {
        // 1:1 после 90 минут, 2:1 в итоге: один гол забит в добавочное время
        Score score = new Score(null, new Goals(1, 1), new Goals(1, 0), null);
        FeedMapper.Mapped m = ApiFootballMapper
                .map(item("AET", new Goals(2, 1), score, null), MOSCOW)
                .orElseThrow();

        assertEquals(MatchStatus.FINISHED, m.status());
        assertEquals(1, m.score().homeScore());
        assertEquals(1, m.score().extraHome());
        assertEquals(0, m.score().extraAway());
        assertEquals("EXTRA_TIME", m.score().duration());
    }

    @Test
    void penaltyShootoutIsSeparateFromTheScore() {
        Score score = new Score(null, new Goals(1, 1), new Goals(0, 0), new Goals(4, 3));
        FeedMapper.Mapped m = ApiFootballMapper
                .map(item("PEN", new Goals(1, 1), score, null), MOSCOW)
                .orElseThrow();

        assertEquals("PENALTY_SHOOTOUT", m.score().duration());
        assertEquals(4, m.score().penHome());
        assertEquals(3, m.score().penAway());
        assertEquals(1, m.score().homeScore());
    }

    @Test
    void postponedAndCancelledAreRecognised() {
        assertEquals(MatchStatus.POSTPONED, ApiFootballMapper.status(new Status("PST", null)));
        assertEquals(MatchStatus.CANCELLED, ApiFootballMapper.status(new Status("ABD", null)));
        assertEquals(MatchStatus.SCHEDULED, ApiFootballMapper.status(null));
    }

    @Test
    void seasonIsTheYearItStarted() {
        assertEquals(2026, ApiFootballSyncService.seasonOf(LocalDate.of(2026, 11, 16)));
        assertEquals(2026, ApiFootballSyncService.seasonOf(LocalDate.of(2027, 3, 1)));
        assertEquals(2025, ApiFootballSyncService.seasonOf(LocalDate.of(2026, 6, 30)));
    }
}
