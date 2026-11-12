package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.round.MatchResolution.DecisionState;
import com.example.predictor.round.MatchResolution.Kind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.example.predictor.round.MatchResolution.resolve;
import static org.junit.jupiter.api.Assertions.*;

class MatchResolutionTest {

    private static final Set<Long> REQUIRED = Set.of(2L, 3L);
    private static final Instant T0 = Instant.parse("2026-11-04T12:00:00Z");

    private static DecisionState decisionState(boolean exists, boolean ownerDeclined, Set<Long> required,
                                               Map<Long, Boolean> votes) {
        return MatchResolution.decisionState(exists, ownerDeclined, required, votes, T0, T0.plusSeconds(60));
    }

    @Test
    void noDecisionYet() {
        assertEquals(DecisionState.NONE, decisionState(false, false, REQUIRED, Map.of()));
    }

    @Test
    void ownerDeclinedMeansDeclined() {
        assertEquals(DecisionState.DECLINED, decisionState(true, true, REQUIRED, Map.of()));
    }

    @Test
    void proposalWaitsForEveryRequiredVoter() {
        assertEquals(DecisionState.VOTING, decisionState(true, false, REQUIRED, Map.of()));
        assertEquals(DecisionState.VOTING, decisionState(true, false, REQUIRED, Map.of(2L, true)));
    }

    @Test
    void allAgreedMeansAccepted() {
        assertEquals(DecisionState.ACCEPTED,
                decisionState(true, false, REQUIRED, Map.of(2L, true, 3L, true)));
    }

    @Test
    void oneDisagreementMeansDeclinedEvenIfOthersHaveNotVoted() {
        assertEquals(DecisionState.DECLINED, decisionState(true, false, REQUIRED, Map.of(3L, false)));
    }

    @Test
    void proposalWithoutOtherVotersIsAcceptedAtOnce() {
        assertEquals(DecisionState.ACCEPTED, decisionState(true, false, Set.of(), Map.of()));
    }

    @Test
    void votesOfNonRequiredUsersAreIgnored() {
        assertEquals(DecisionState.ACCEPTED,
                decisionState(true, false, Set.of(2L), Map.of(2L, true, 9L, false)));
    }

    @Test
    void votingExpiresAfterOneDayAndMatchIsExcluded() {
        Instant justBefore = T0.plus(MatchResolution.VOTING_PERIOD).minusSeconds(1);
        Instant atDeadline = T0.plus(MatchResolution.VOTING_PERIOD);
        assertEquals(DecisionState.VOTING,
                MatchResolution.decisionState(true, false, REQUIRED, Map.of(2L, true), T0, justBefore));
        assertEquals(DecisionState.EXPIRED,
                MatchResolution.decisionState(true, false, REQUIRED, Map.of(2L, true), T0, atDeadline));
        assertEquals(Kind.EXCLUDED, resolve(MatchStatus.FINISHED, true, true, DecisionState.EXPIRED));
    }

    @Test
    void lastAgreementAfterDeadlineStillCountsAsAcceptedOnlyIfAllAgreed() {
        Instant late = T0.plus(MatchResolution.VOTING_PERIOD).plusSeconds(5);
        assertEquals(DecisionState.ACCEPTED,
                MatchResolution.decisionState(true, false, REQUIRED, Map.of(2L, true, 3L, true), T0, late));
    }

    @Test
    void unplayedAndCancelledMatches() {
        assertEquals(Kind.CANCELLED, resolve(MatchStatus.CANCELLED, false, false, DecisionState.NONE));
        assertEquals(Kind.NOT_PLAYED, resolve(MatchStatus.LIVE, false, false, DecisionState.NONE));
        assertEquals(Kind.NOT_PLAYED, resolve(MatchStatus.POSTPONED, false, false, DecisionState.NONE));
        assertEquals(Kind.NOT_PLAYED, resolve(MatchStatus.FINISHED, false, false, DecisionState.NONE));
    }

    @Test
    void completeDataCountsFullyWhateverTheDecisionSays() {
        assertEquals(Kind.FULL, resolve(MatchStatus.FINISHED, true, false, DecisionState.NONE));
        assertEquals(Kind.FULL, resolve(MatchStatus.FINISHED, true, false, DecisionState.DECLINED));
    }

    @Test
    void missingDataFollowsTheDecision() {
        assertEquals(Kind.NEEDS_DECISION, resolve(MatchStatus.FINISHED, true, true, DecisionState.NONE));
        assertEquals(Kind.VOTING, resolve(MatchStatus.FINISHED, true, true, DecisionState.VOTING));
        assertEquals(Kind.REGULAR_ONLY, resolve(MatchStatus.FINISHED, true, true, DecisionState.ACCEPTED));
        assertEquals(Kind.EXCLUDED, resolve(MatchStatus.FINISHED, true, true, DecisionState.DECLINED));
    }

    @Test
    void settledAndCountedFlags() {
        assertTrue(Kind.FULL.settled() && Kind.FULL.counted());
        assertTrue(Kind.REGULAR_ONLY.settled() && Kind.REGULAR_ONLY.counted());
        assertTrue(Kind.EXCLUDED.settled() && !Kind.EXCLUDED.counted());
        assertTrue(Kind.CANCELLED.settled() && !Kind.CANCELLED.counted());
        assertFalse(Kind.NEEDS_DECISION.settled());
        assertFalse(Kind.VOTING.settled());
        assertFalse(Kind.NOT_PLAYED.settled());
    }
}
