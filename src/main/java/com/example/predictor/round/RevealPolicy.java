package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RevealMode;

import java.time.Instant;

/** Когда чужие прогнозы на матч можно показывать. Чистая логика. */
public final class RevealPolicy {

    private RevealPolicy() {
    }

    /** Приём прогнозов на матч закрыт: наступило время закрытия или матч уже идёт или сыгран. */
    public static boolean locked(Instant now, Instant lockAt, MatchStatus status) {
        return !now.isBefore(lockAt) || status == MatchStatus.LIVE || status == MatchStatus.FINISHED;
    }

    /**
     * Чужие прогнозы на матч видны, когда приём прогнозов на него закрыт: тогда их видят все участники
     * группы, в том числе те, кто свой прогноз не сохранил (повлиять на прогнозы уже нельзя).
     * Пока приём открыт, чужие прогнозы видит только тот, кто уже сохранил свой прогноз на этот матч
     * (так чужой выбор не может повлиять на его собственный), и только в режиме AFTER_OWN_SUBMIT.
     * В режиме AT_LOCK до закрытия приёма их не видит никто.
     */
    public static boolean othersVisible(RevealMode mode, boolean locked, boolean iPredicted) {
        if (locked) {
            return true;
        }
        return iPredicted && mode == RevealMode.AFTER_OWN_SUBMIT;
    }
}
