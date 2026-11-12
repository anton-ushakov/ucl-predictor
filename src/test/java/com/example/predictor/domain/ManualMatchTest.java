package com.example.predictor.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Матч, добавленный владельцем группы вручную: счёт вводится руками, из источника он не придёт. */
class ManualMatchTest {

    private static final Instant KICKOFF = Instant.parse("2026-11-10T19:00:00Z");
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);
    private static final ScoreBasis REGULAR = new ScoreBasis(false, false);
    private static final ScoreBasis WITH_EXTRA = new ScoreBasis(true, false);
    private static final ScoreBasis WITH_ALL = new ScoreBasis(true, true);

    private static FootballMatch manual() {
        return new FootballMatch(null, 7L, 3L, "Спартак", "Зенит", KICKOFF, WEEK, "Тур 15");
    }

    @Test
    void newManualMatchIsNotPlayedYet() {
        FootballMatch m = manual();
        assertTrue(m.isManual());
        assertNull(m.getExternalId());
        assertEquals(MatchStatus.SCHEDULED, m.getStatus());
        assertFalse(m.isFinished());
        assertEquals(Long.valueOf(7L), m.getOwnerGroupId());
        assertEquals(Long.valueOf(3L), m.getCreatedBy());
    }

    @Test
    void feedMatchIsNotManual() {
        assertFalse(new FootballMatch(12345L).isManual());
    }

    @Test
    void regularScoreMakesMatchFinished() {
        FootballMatch m = manual();
        m.setManualScore(2, 1, null, null, null, null);
        assertTrue(m.isFinished());
        assertEquals(2, m.scoreHome(REGULAR));
        assertEquals(1, m.scoreAway(REGULAR));
        assertFalse(m.hadExtraTime());
        assertFalse(m.hadPenalties());
    }

    @Test
    void extraTimeIsAddedOnlyWhenGroupCountsIt() {
        FootballMatch m = manual();
        m.setManualScore(1, 1, 1, 0, null, null);
        assertTrue(m.hadExtraTime());
        assertFalse(m.hadPenalties());
        assertEquals(1, m.scoreHome(REGULAR));
        assertEquals(2, m.scoreHome(WITH_EXTRA));
        assertEquals(1, m.scoreAway(WITH_EXTRA));
    }

    @Test
    void penaltiesAreAddedOnlyWhenGroupCountsThem() {
        FootballMatch m = manual();
        m.setManualScore(1, 1, 0, 0, 4, 3);
        assertTrue(m.hadPenalties());
        assertEquals(1, m.scoreHome(WITH_EXTRA));
        assertEquals(5, m.scoreHome(WITH_ALL));
        assertEquals(4, m.scoreAway(WITH_ALL));
    }

    @Test
    void emptyScoreReturnsMatchToScheduled() {
        FootballMatch m = manual();
        m.setManualScore(2, 1, null, null, null, null);
        m.setManualScore(null, null, null, null, null, null);
        assertEquals(MatchStatus.SCHEDULED, m.getStatus());
        assertFalse(m.isFinished());
        assertNull(m.getHomeScore());
    }

    @Test
    void manualScoreNeverNeedsAGroupDecision() {
        FootballMatch m = manual();
        m.setManualScore(1, 1, 1, 0, null, null);
        assertTrue(m.missingParts(WITH_ALL).isEmpty());
    }

    @Test
    void cancelledMatchIsNotCounted() {
        FootballMatch m = manual();
        m.setManualScore(2, 1, null, null, null, null);
        m.cancelManual();
        assertEquals(MatchStatus.CANCELLED, m.getStatus());
        assertFalse(m.isFinished());
    }

    @Test
    void ownerCanFixTeamsAndTime() {
        FootballMatch m = manual();
        Instant later = Instant.parse("2026-11-12T17:30:00Z");
        m.editManual("Зенит", "Спартак", later, WEEK, "Тур 16");
        assertEquals("Зенит", m.getHomeTeam());
        assertEquals("Спартак", m.getAwayTeam());
        assertEquals(later, m.getKickoffAt());
        assertEquals("Тур 16", m.getStage());
    }
}
