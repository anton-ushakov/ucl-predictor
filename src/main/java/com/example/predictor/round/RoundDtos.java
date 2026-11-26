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

    /** joker=true: участник поставил на этот матч джокер, баллы за него удвоены. */
    public record OtherPrediction(Long userId, String username, String badge,
                                  int homeGoals, int awayGoals,
                                  Instant savedAt, Integer points, boolean joker,
                                  boolean leader, boolean lastWinner) {
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
    /** competitionCode — обозначение турнира (CL, PL, …): по нему приложение выбирает оформление. */
    public record MatchView(Long matchId, String homeTeam, String awayTeam,
                            String competitionCode, String competition,
                            String homeCrest, String awayCrest, Instant kickoffAt,
                            Instant lockAt, String stage, Integer matchday, MatchStatus status,
                            Integer homeScore, Integer awayScore,
                            Integer liveHome, Integer liveAway, ScoreDetail scoreDetail,
                            String othersSummary,
                            String counting, ScoreDecisionView scoreDecision,
                            boolean open, boolean locked,
                            int predictedCount, int memberCount, MyPrediction mine,
                            boolean othersVisible, List<OtherPrediction> others,
                            /** Справки от источника: форма команд и личные встречи. */
                            String homeForm, String awayForm, HeadToHeadView headToHead) {
    }

    /** Личные встречи: сколько матчей и как они разошлись. */
    public record HeadToHeadView(int total, int homeWins, int draws, int awayWins) {
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

    /** livePoints: баллы, которые набегают прямо сейчас по идущим матчам. */
    /**
     * tiebreak: показатели этого участника, когда по баллам он сравнялся с кем-то ещё
     * («точных счетов 2 · ошибка по голам 7»). У остальных пусто: их место никто не
     * оспаривает, и подпись только мешала бы читать таблицу.
     */
    public record StandingRow(Long userId, String username, String badge,
                              int place, int points, int livePoints, int exactScores,
                              int outcomeHits, int goalDiffHits, int goalError,
                              boolean winner, boolean loser, boolean leader, boolean lastWinner,
                              String tiebreak) {
    }

    public record RoundView(LocalDate week, RoundStatus status, RulesDto rules, List<MatchView> matches,
                            boolean canSubmit, int openMatches, String submitWarning,
                            boolean standingsFinal, List<StandingRow> standings,
                            List<String> buyers, List<String> receivers, List<String> notParticipated,
                            int pendingDecisions, int decisionsForMe, String note,
                            boolean jokerEnabled, Long jokerMatchId, int liveMatches,
                            int roundNo, String title, Instant firstKickoff, Instant lastKickoff,
                            boolean friendly, String tiebreakNote,
                            /** Владельцу тура показывается пересчёт итогов по текущему счёту. */
                            boolean canRecount) {
    }

    /**
     * matches: сколько матчей владелец отобрал в этот тур. selectable=true: состав тура ещё можно
     * менять (это текущая или следующая неделя), и владельцу группы показывается кнопка выбора матчей.
     * competitions: короткие обозначения турниров тура («ЛЧ», «АПЛ») — по ним видно, что внутри,
     * не заходя в тур; winner: кто выиграл тур, когда он уже сыгран.
     */
    public record RoundSummary(LocalDate week, RoundStatus status, int matches, int predictedByMe,
                               int openForMe, Instant firstKickoff, boolean selectable,
                               int membersPredicted, int memberCount, boolean canSelect,
                               int roundNo, String title, Instant lastKickoff, boolean friendly,
                               List<String> competitions, String winner) {
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
