package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Как матч учитывается в туре группы. Чистая логика.
 *
 * <p>Если в группе включён счёт с добавочным временем и/или пенальти, а football-data.org ещё не отдала
 * эти данные, матч нельзя посчитать по правилам группы. Тогда владелец решает: предложить посчитать матч
 * только по основному времени (с согласия всех остальных участников тура) или не учитывать его.
 */
public final class MatchResolution {

    public enum Kind {
        /** Матч не сыгран (или перенесён): тур подвести нельзя. */
        NOT_PLAYED,
        /** Матч отменён: не учитывается. */
        CANCELLED,
        /** Матч считается по правилам группы. */
        FULL,
        /** Матч считается только по основному времени: владелец предложил, все согласились. */
        REGULAR_ONLY,
        /** Матч не учитывается: владелец не согласен или кто-то из участников против. */
        EXCLUDED,
        /** Нет данных, ждём решения владельца. */
        NEEDS_DECISION,
        /** Владелец предложил считать по основному времени, ждём согласия остальных. */
        VOTING;

        /** Судьба матча решена: тур можно подводить. */
        public boolean settled() {
            return this == CANCELLED || this == FULL || this == REGULAR_ONLY || this == EXCLUDED;
        }

        /** Матч идёт в зачёт. */
        public boolean counted() {
            return this == FULL || this == REGULAR_ONLY;
        }

        public boolean awaitingDecision() {
            return this == NEEDS_DECISION || this == VOTING;
        }
    }

    /** EXPIRED: за сутки с предложения не все проголосовали (и никто не был против): матч не учитывается. */
    public enum DecisionState {
        NONE, VOTING, ACCEPTED, DECLINED, EXPIRED
    }

    /** Сколько времени участникам даётся на голосование, считая от предложения владельца. */
    public static final Duration VOTING_PERIOD = Duration.ofDays(1);

    private MatchResolution() {
    }

    /**
     * Состояние решения. required: участники тура, чьё согласие нужно (все, кто сделал прогнозы на тур,
     * кроме владельца: его согласие это само предложение). votes: userId -> согласен ли.
     * Один голос «против» сразу означает, что матч не учитывается. Если за сутки с момента предложения
     * согласились не все, матч тоже не учитывается (EXPIRED).
     */
    public static DecisionState decisionState(boolean exists, boolean ownerDeclined,
                                              Set<Long> required, Map<Long, Boolean> votes,
                                              Instant proposedAt, Instant now) {
        if (!exists) {
            return DecisionState.NONE;
        }
        if (ownerDeclined) {
            return DecisionState.DECLINED;
        }
        boolean allAgreed = true;
        for (Long userId : required) {
            Boolean vote = votes.get(userId);
            if (vote == null) {
                allAgreed = false;
            } else if (!vote) {
                return DecisionState.DECLINED;
            }
        }
        if (allAgreed) {
            return DecisionState.ACCEPTED;
        }
        return now.isBefore(proposedAt.plus(VOTING_PERIOD)) ? DecisionState.VOTING : DecisionState.EXPIRED;
    }

    /**
     * hasRegularScore: матч сыгран и счёт основного времени известен.
     * missingParts: для правил группы не хватает данных о добавочном времени или пенальти.
     */
    public static Kind resolve(MatchStatus status, boolean hasRegularScore, boolean missingParts,
                               DecisionState decision) {
        if (status == MatchStatus.CANCELLED) {
            return Kind.CANCELLED;
        }
        if (status != MatchStatus.FINISHED || !hasRegularScore) {
            return Kind.NOT_PLAYED;
        }
        if (!missingParts) {
            return Kind.FULL;
        }
        return switch (decision) {
            case ACCEPTED -> Kind.REGULAR_ONLY;
            case DECLINED, EXPIRED -> Kind.EXCLUDED;
            case VOTING -> Kind.VOTING;
            case NONE -> Kind.NEEDS_DECISION;
        };
    }
}
