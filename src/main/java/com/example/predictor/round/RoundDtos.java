package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RevealMode;
import com.example.predictor.domain.SubmissionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Формы данных, которые API отдаёт и принимает для туров и прогнозов. */
public final class RoundDtos {

    private RoundDtos() {
    }

    /** Правила тура. frozen=true: правила зафиксированы, изменение настроек группы их не затронет. */
    public record RulesDto(int lockMinutesBefore, List<String> tiebreakers, RevealMode revealMode,
                           boolean countExtraTime, boolean countPenalties, String scoreDescription,
                           SubmissionMode submissionMode, String submissionDescription,
                           int exactScorePoints, int goalDiffPoints, int outcomePoints,
                           String scoringDescription, boolean frozen) {
    }

    /**
     * Счёт матча по частям: основное время, добавочное время и серия пенальти
     * (null, если такого периода не было). Для подсчёта баллов берётся сумма частей,
     * выбранных в настройках группы.
     */
    public record ScoreDetail(Integer regularHome, Integer regularAway, Integer extraHome, Integer extraAway,
                              Integer penaltiesHome, Integer penaltiesAway) {
    }

    public record MyPrediction(int homeGoals, int awayGoals, Instant savedAt, Integer points) {
    }

    public record OtherPrediction(Long userId, String username, int homeGoals, int awayGoals,
                                  Instant savedAt, Integer points) {
    }

    /**
     * homeScore и awayScore: счёт, по которому считаются баллы в этой группе (по её настройкам),
     * scoreDetail: счёт по частям. predictedCount и memberCount видны всегда (сколько участников уже
     * сделали прогноз), а сами чужие прогнозы только когда othersVisible=true, то есть после того,
     * как вы сохранили свой прогноз на этот матч, или после закрытия приёма прогнозов на матч (тогда их
     * видят все участники группы).
     * counting: как матч учитывается (FULL, REGULAR_ONLY, EXCLUDED, NEEDS_DECISION, VOTING, NOT_PLAYED,
     * CANCELLED). homeScore и awayScore пусты, пока матч не идёт в зачёт.
     */
    public record MatchView(Long matchId, String homeTeam, String awayTeam, Instant kickoffAt,
                            Instant lockAt, String stage, Integer matchday, MatchStatus status,
                            Integer homeScore, Integer awayScore, ScoreDetail scoreDetail,
                            String counting, ScoreDecisionView scoreDecision,
                            boolean open, boolean locked,
                            int predictedCount, int memberCount, MyPrediction mine,
                            boolean othersVisible, List<OtherPrediction> others) {
    }

    /**
     * Решение по матчу без полных данных. state: NEEDS_DECISION (ждём владельца), VOTING (ждём
     * согласия участников), ACCEPTED (считаем по основному времени), DECLINED (матч не учитывается).
     * missing: чего не хватает (EXTRA_TIME, PENALTIES). canPropose: вы владелец и можете решить.
     * canVote: вы можете согласиться или отказаться.
     * votingEndsAt: до какого времени идёт голосование (сутки с предложения), иначе null. message: готовый текст для экрана.
     */
    public record ScoreDecisionView(String state, List<String> missing, boolean canPropose, boolean canVote,
                                    Boolean myVote, List<String> agreed, List<String> disagreed,
                                    List<String> waiting, Instant votingEndsAt, String message) {
    }

    /** agree=true: согласен считать матч только по основному времени. */
    public record VoteRequest(@NotNull Boolean agree) {
    }

    /** countRegularTimeOnly=true: предложить считать матч по основному времени; false: не учитывать матч. */
    public record ProposeRequest(@NotNull Boolean countRegularTimeOnly) {
    }

    public record StandingRow(Long userId, String username, int place, int points, int exactScores,
                              int outcomeHits, int goalDiffHits, int goalError,
                              boolean winner, boolean loser) {
    }

    public record RoundView(LocalDate week, RoundStatus status, RulesDto rules, List<MatchView> matches,
                            boolean canSubmit, int openMatches, String submitWarning,
                            boolean standingsFinal, List<StandingRow> standings,
                            List<String> buyers, List<String> receivers, List<String> notParticipated,
                            int pendingDecisions, int decisionsForMe, String note) {
    }

    /**
     * matches: сколько матчей владелец отобрал в этот тур. selectable=true: состав тура ещё можно
     * менять (это текущая или следующая неделя), и владельцу группы показывается кнопка выбора матчей.
     */
    public record RoundSummary(LocalDate week, RoundStatus status, int matches, int predictedByMe,
                               int openForMe, Instant firstKickoff, boolean selectable) {
    }

    public record Entry(@NotNull Long matchId,
                        @NotNull @Min(0) @Max(20) Integer homeGoals,
                        @NotNull @Min(0) @Max(20) Integer awayGoals) {
    }

    /** confirmed=true: пользователь увидел предупреждение, что прогнозы нельзя изменить. */
    public record SubmitRequest(@NotNull Boolean confirmed,
                                @NotEmpty @Size(max = 40) @Valid List<Entry> predictions) {
    }

    public record SubmitResult(int saved, Instant savedAt, String message) {
    }
}
