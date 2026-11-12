package com.example.predictor.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FootballMatchScoreTest {

    private static FootballMatch finished(int h, int a, Integer eh, Integer ea, Integer ph, Integer pa) {
        FootballMatch m = new FootballMatch(1L);
        m.applyFeed("A", "B", Instant.parse("2026-11-04T20:00:00Z"), LocalDate.of(2026, 11, 2),
                "LEAGUE_STAGE", 4, MatchStatus.FINISHED, h, a, eh, ea, ph, pa,
                ph != null ? "PENALTY_SHOOTOUT" : eh != null ? "EXTRA_TIME" : "REGULAR");
        return m;
    }

    @Test
    void regularTimeIsDefault() {
        FootballMatch m = finished(1, 1, 1, 0, 4, 3);
        assertEquals(1, m.scoreHome(ScoreBasis.REGULAR_TIME));
        assertEquals(1, m.scoreAway(ScoreBasis.REGULAR_TIME));
    }

    @Test
    void extraTimeIsAddedWhenEnabled() {
        FootballMatch m = finished(1, 1, 1, 0, 4, 3);
        ScoreBasis basis = new ScoreBasis(true, false);
        assertEquals(2, m.scoreHome(basis));
        assertEquals(1, m.scoreAway(basis));
    }

    @Test
    void penaltiesAreAddedWhenEnabled() {
        FootballMatch m = finished(1, 1, 0, 0, 4, 3);
        ScoreBasis basis = new ScoreBasis(false, true);
        assertEquals(5, m.scoreHome(basis));
        assertEquals(4, m.scoreAway(basis));
    }

    @Test
    void bothAreAddedWhenBothEnabled() {
        FootballMatch m = finished(1, 1, 1, 0, 4, 3);
        ScoreBasis basis = new ScoreBasis(true, true);
        assertEquals(6, m.scoreHome(basis));
        assertEquals(4, m.scoreAway(basis));
    }

    @Test
    void matchWithoutOvertimeIsUnaffectedByBasis() {
        FootballMatch m = finished(2, 0, null, null, null, null);
        assertEquals(2, m.scoreHome(new ScoreBasis(true, true)));
        assertEquals(0, m.scoreAway(new ScoreBasis(true, true)));
    }

    @Test
    void unfinishedMatchHasNoScore() {
        FootballMatch m = new FootballMatch(2L);
        m.applyFeed("A", "B", Instant.parse("2026-11-04T20:00:00Z"), LocalDate.of(2026, 11, 2),
                "LEAGUE_STAGE", 4, MatchStatus.FINISHED, null, null, null, null, null, null, null);
        assertFalse(m.isFinished());
        assertNull(m.scoreHome(new ScoreBasis(true, true)));
    }

    @Test
    void missingPartsAreReportedOnlyForPartsTheGroupCounts() {
        FootballMatch m = new FootballMatch(3L);
        m.applyFeed("A", "B", Instant.parse("2026-11-04T20:00:00Z"), LocalDate.of(2026, 11, 2),
                "LEAGUE_STAGE", 4, MatchStatus.FINISHED, 1, 1, 0, 0, null, null, "PENALTY_SHOOTOUT");
        assertEquals(java.util.List.of(), m.missingParts(ScoreBasis.REGULAR_TIME));
        assertEquals(java.util.List.of(), m.missingParts(new ScoreBasis(true, false)));
        assertEquals(java.util.List.of("PENALTIES"), m.missingParts(new ScoreBasis(false, true)));
        assertEquals(java.util.List.of("PENALTIES"), m.missingParts(new ScoreBasis(true, true)));

        FootballMatch noExtra = new FootballMatch(4L);
        noExtra.applyFeed("A", "B", Instant.parse("2026-11-04T20:00:00Z"), LocalDate.of(2026, 11, 2),
                "LEAGUE_STAGE", 4, MatchStatus.FINISHED, 1, 1, null, null, 4, 3, "PENALTY_SHOOTOUT");
        assertEquals(java.util.List.of("EXTRA_TIME"),
                noExtra.missingParts(new ScoreBasis(true, true)));
    }

    @Test
    void regularMatchNeverMissesParts() {
        assertEquals(java.util.List.of(), finished(2, 0, null, null, null, null).missingParts(new ScoreBasis(true, true)));
    }
}
