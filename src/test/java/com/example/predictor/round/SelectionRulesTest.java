package com.example.predictor.round;

import com.example.predictor.round.SelectionRules.Problem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectionRulesTest {

    private static final LocalDate CURRENT = LocalDate.of(2026, 11, 9);
    private static final LocalDate NEXT = LocalDate.of(2026, 11, 16);

    @Test
    void matchOfCurrentWeekCanBeAdded() {
        assertNull(SelectionRules.canAdd("Реал — Барселона", CURRENT, CURRENT, NEXT, false));
    }

    @Test
    void matchOfNextWeekCanBeAdded() {
        assertNull(SelectionRules.canAdd("Реал — Барселона", NEXT, CURRENT, NEXT, false));
    }

    @Test
    void pastWeekIsRejected() {
        Problem p = SelectionRules.canAdd("Реал — Барселона", CURRENT.minusWeeks(1), CURRENT, NEXT, false);
        assertNotNull(p);
        assertEquals(409, p.status());
    }

    @Test
    void tooFarAheadIsRejected() {
        assertNotNull(SelectionRules.canAdd("Реал — Барселона", NEXT.plusWeeks(1), CURRENT, NEXT, false));
    }

    @Test
    void lockedMatchIsRejected() {
        Problem p = SelectionRules.canAdd("Реал — Барселона", CURRENT, CURRENT, NEXT, true);
        assertNotNull(p);
        assertEquals(409, p.status());
    }

    @Test
    void matchWithoutPredictionsCanBeRemoved() {
        assertNull(SelectionRules.canRemove(true, false));
    }

    @Test
    void matchWithPredictionsCannotBeRemoved() {
        Problem p = SelectionRules.canRemove(true, true);
        assertNotNull(p);
        assertEquals(409, p.status());
    }

    @Test
    void matchOutsideRoundGivesNotFound() {
        Problem p = SelectionRules.canRemove(false, false);
        assertNotNull(p);
        assertEquals(404, p.status());
    }

    @Test
    void sameTeamTwiceIsRejected() {
        Problem p = SelectionRules.checkTeams("Спартак", " спартак ");
        assertNotNull(p);
        assertEquals(400, p.status());
    }

    @Test
    void emptyTeamIsRejected() {
        assertNotNull(SelectionRules.checkTeams("Спартак", "  "));
    }

    @Test
    void differentTeamsAreAccepted() {
        assertNull(SelectionRules.checkTeams("Спартак", "Зенит"));
    }

    @Test
    void manualMatchMustBeInPredictableWeeks() {
        assertNull(SelectionRules.canPlaceManual(CURRENT, CURRENT, NEXT));
        assertNotNull(SelectionRules.canPlaceManual(CURRENT.minusWeeks(1), CURRENT, NEXT));
        assertNotNull(SelectionRules.canPlaceManual(NEXT.plusWeeks(2), CURRENT, NEXT));
    }

    @Test
    void scoreBeforeLockIsRejected() {
        Problem p = SelectionRules.checkManualScore(false, null, null, null, null);
        assertNotNull(p);
        assertEquals(409, p.status());
    }

    @Test
    void regularScoreAfterLockIsAccepted() {
        assertNull(SelectionRules.checkManualScore(true, null, null, null, null));
    }

    @Test
    void extraTimeNeedsBothTeams() {
        assertNotNull(SelectionRules.checkManualScore(true, 1, null, null, null));
        assertNull(SelectionRules.checkManualScore(true, 1, 0, null, null));
    }

    @Test
    void penaltiesNeedBothTeams() {
        assertNotNull(SelectionRules.checkManualScore(true, 0, 0, 4, null));
        assertNull(SelectionRules.checkManualScore(true, 0, 0, 4, 3));
    }

    @Test
    void penaltiesWithoutExtraTimeIsRejected() {
        Problem p = SelectionRules.checkManualScore(true, null, null, 4, 3);
        assertNotNull(p);
        assertEquals(400, p.status());
    }
}
