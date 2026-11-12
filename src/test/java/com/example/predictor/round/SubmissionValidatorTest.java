package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.SubmissionMode;
import com.example.predictor.round.SubmissionValidator.Entry;
import com.example.predictor.round.SubmissionValidator.MatchState;
import com.example.predictor.round.SubmissionValidator.SubmissionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-11-04T17:00:00Z");
    private static final Instant PAST = Instant.parse("2026-11-03T11:00:00Z");

    private static final LocalDate TUE = LocalDate.of(2026, 11, 3);
    private static final LocalDate WED = LocalDate.of(2026, 11, 4);

    private static MatchState open(long id) {
        return open(id, FUTURE, TUE);
    }

    private static MatchState open(long id, Instant lockAt, LocalDate day) {
        return new MatchState(id, "Команда" + id + " — Соперник" + id, MatchStatus.SCHEDULED,
                lockAt, false, day);
    }

    private static MatchState state(long id, String title, MatchStatus status, Instant lockAt,
                                    boolean alreadyPredicted) {
        return new MatchState(id, title, status, lockAt, alreadyPredicted, TUE);
    }

    private static void validate(List<MatchState> matches, List<Entry> entries) {
        SubmissionValidator.validate(NOW, true, matches, entries, SubmissionMode.WHOLE_ROUND);
    }

    private static SubmissionException fails(boolean confirmed, List<MatchState> matches, List<Entry> entries) {
        return fails(confirmed, matches, entries, SubmissionMode.WHOLE_ROUND);
    }

    private static SubmissionException fails(boolean confirmed, List<MatchState> matches, List<Entry> entries,
                                             SubmissionMode mode) {
        return assertThrows(SubmissionException.class,
                () -> SubmissionValidator.validate(NOW, confirmed, matches, entries, mode));
    }

    @Test
    void allOpenMatchesPredictedIsAccepted() {
        validate(List.of(open(1), open(2)), List.of(new Entry(1, 2, 0), new Entry(2, 1, 1)));
    }

    @Test
    void withoutConfirmationNothingIsAccepted() {
        SubmissionException e = fails(false, List.of(open(1)), List.of(new Entry(1, 1, 0)));
        assertEquals(400, e.status());
        assertTrue(e.getMessage().contains("Подтвердите"));
    }

    @Test
    void emptySubmissionIsRejected() {
        assertEquals(400, fails(true, List.of(open(1)), List.of()).status());
    }

    @Test
    void missingOpenMatchIsRejectedAndNamed() {
        SubmissionException e = fails(true, List.of(open(1), open(2)), List.of(new Entry(1, 1, 0)));
        assertEquals(400, e.status());
        assertTrue(e.getMessage().contains("Команда2 — Соперник2"));
    }

    @Test
    void duplicateAndUnknownMatchesAreRejected() {
        assertEquals(400, fails(true, List.of(open(1)),
                List.of(new Entry(1, 1, 0), new Entry(1, 2, 0))).status());
        assertEquals(400, fails(true, List.of(open(1)), List.of(new Entry(99, 1, 0))).status());
    }

    @Test
    void negativeGoalsAreRejected() {
        assertEquals(400, fails(true, List.of(open(1)), List.of(new Entry(1, -1, 0))).status());
    }

    @Test
    void lockedMatchIsRejectedWithConflict() {
        MatchState locked = state(1, "A — B", MatchStatus.SCHEDULED, PAST, false);
        assertEquals(409, fails(true, List.of(locked), List.of(new Entry(1, 1, 0))).status());
    }

    @Test
    void lockMomentItselfIsAlreadyClosed() {
        MatchState atLock = state(1, "A — B", MatchStatus.SCHEDULED, NOW, false);
        assertEquals(409, fails(true, List.of(atLock), List.of(new Entry(1, 1, 0))).status());
    }

    @Test
    void alreadySavedPredictionCannotBeChanged() {
        MatchState saved = state(1, "A — B", MatchStatus.SCHEDULED, FUTURE, true);
        SubmissionException e = fails(true, List.of(saved), List.of(new Entry(1, 3, 3)));
        assertEquals(409, e.status());
        assertTrue(e.getMessage().contains("не может быть изменён"));
    }

    @Test
    void postponedOrLiveMatchIsRejected() {
        MatchState postponed = state(1, "A — B", MatchStatus.POSTPONED, FUTURE, false);
        assertEquals(409, fails(true, List.of(postponed), List.of(new Entry(1, 1, 0))).status());
        MatchState live = state(2, "C — D", MatchStatus.LIVE, FUTURE, false);
        assertEquals(409, fails(true, List.of(live), List.of(new Entry(2, 1, 0))).status());
    }

    @Test
    void closedAndSavedMatchesAreNotRequiredInNewSubmission() {
        // 1 уже сохранён раньше, 2 уже закрыт, 3 открыт: достаточно указать только 3
        List<MatchState> matches = List.of(
                state(1, "A — B", MatchStatus.SCHEDULED, FUTURE, true),
                state(2, "C — D", MatchStatus.SCHEDULED, PAST, false),
                open(3));
        validate(matches, List.of(new Entry(3, 0, 0)));
    }

    @Test
    void postponedMatchIsNotRequired() {
        List<MatchState> matches = List.of(
                state(1, "A — B", MatchStatus.POSTPONED, FUTURE, false), open(2));
        validate(matches, List.of(new Entry(2, 1, 2)));
    }

    // ------------------------------------------------------------------ режимы сдачи

    @Test
    void byDayModeRequiresTheWholeDayButNotOtherDays() {
        List<MatchState> matches = List.of(
                open(1, FUTURE, TUE), open(2, FUTURE, TUE), open(3, FUTURE, WED));
        // вторник целиком: среду сдавать не обязательно
        SubmissionValidator.validate(NOW, true, matches,
                List.of(new Entry(1, 1, 0), new Entry(2, 0, 0)), SubmissionMode.BY_DAY);
        // половина вторника не принимается
        SubmissionException e = fails(true, matches, List.of(new Entry(1, 1, 0)), SubmissionMode.BY_DAY);
        assertEquals(400, e.status());
        assertTrue(e.getMessage().contains("Команда2 — Соперник2"));
    }

    @Test
    void byDayModeAllowsSeveralDaysAtOnce() {
        List<MatchState> matches = List.of(
                open(1, FUTURE, TUE), open(2, FUTURE, WED));
        SubmissionValidator.validate(NOW, true, matches,
                List.of(new Entry(1, 1, 0), new Entry(2, 2, 2)), SubmissionMode.BY_DAY);
    }

    @Test
    void wholeRoundModeRequiresEveryDay() {
        List<MatchState> matches = List.of(open(1, FUTURE, TUE), open(2, FUTURE, WED));
        SubmissionException e = fails(true, matches, List.of(new Entry(1, 1, 0)));
        assertTrue(e.getMessage().contains("весь тур сразу"));
    }

    @Test
    void freeModeAcceptsASingleMatch() {
        List<MatchState> matches = List.of(open(1, FUTURE, TUE), open(2, FUTURE, TUE), open(3, FUTURE, WED));
        SubmissionValidator.validate(NOW, true, matches, List.of(new Entry(2, 3, 1)), SubmissionMode.FREE);
    }

    @Test
    void freeModeStillChecksClosedMatchesAndConfirmation() {
        List<MatchState> matches = List.of(state(1, "A — B", MatchStatus.SCHEDULED, PAST, false));
        assertEquals(409, fails(true, matches, List.of(new Entry(1, 1, 0)), SubmissionMode.FREE).status());
        assertEquals(400, fails(false, matches, List.of(new Entry(1, 1, 0)), SubmissionMode.FREE).status());
    }
}
