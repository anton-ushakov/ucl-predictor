package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RevealMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RevealPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void notLockedBeforeLockTime() {
        assertFalse(RevealPolicy.locked(NOW, NOW.plusSeconds(60), MatchStatus.SCHEDULED));
    }

    @Test
    void lockedAtAndAfterLockTime() {
        assertTrue(RevealPolicy.locked(NOW, NOW, MatchStatus.SCHEDULED));
        assertTrue(RevealPolicy.locked(NOW, NOW.minusSeconds(1), MatchStatus.SCHEDULED));
    }

    @Test
    void liveOrFinishedMatchIsLockedEvenIfTimeSaysOtherwise() {
        assertTrue(RevealPolicy.locked(NOW, NOW.plusSeconds(3600), MatchStatus.LIVE));
        assertTrue(RevealPolicy.locked(NOW, NOW.plusSeconds(3600), MatchStatus.FINISHED));
    }

    @Test
    void afterOwnSubmitModeShowsOthersRightAfterOwnSaveWhileOpen() {
        assertTrue(RevealPolicy.othersVisible(RevealMode.AFTER_OWN_SUBMIT, false, true));
    }

    @Test
    void whoDidNotSaveSeesNothingWhileMatchIsOpen() {
        for (RevealMode mode : RevealMode.values()) {
            assertFalse(RevealPolicy.othersVisible(mode, false, false));
        }
    }

    @Test
    void atLockModeShowsNothingWhileOpenEvenAfterOwnSave() {
        assertFalse(RevealPolicy.othersVisible(RevealMode.AT_LOCK, false, true));
        assertFalse(RevealPolicy.othersVisible(RevealMode.AT_LOCK, false, false));
    }

    @Test
    void afterLockEveryoneSeesEverythingInAnyMode() {
        for (RevealMode mode : RevealMode.values()) {
            assertTrue(RevealPolicy.othersVisible(mode, true, true));
            assertTrue(RevealPolicy.othersVisible(mode, true, false));
        }
    }
}
