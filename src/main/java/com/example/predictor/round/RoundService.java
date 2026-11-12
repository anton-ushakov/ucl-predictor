package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.Prediction;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.domain.SubmissionMode;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.round.RoundDtos.*;
import com.example.predictor.round.SubmissionValidator.SubmissionException;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.standings.RoundWeeks;
import com.example.predictor.standings.StandingsCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Туры (недели): список, экран тура с прогнозами и сохранение прогнозов. */
@Service
public class RoundService {

    private static final String SUBMIT_WARNING =
            "После сохранения прогнозы изменить нельзя: ни вам, ни владельцу группы.";

    /** Текст для окна подтверждения: напоминает и про необратимость, и про режим сдачи. */
    static String submitWarning(SubmissionMode mode) {
        return SUBMIT_WARNING + " " + switch (mode) {
            case WHOLE_ROUND -> "Прогнозы сдаются на весь тур сразу.";
            case BY_DAY -> "Игровой день сдаётся целиком.";
            case FREE -> "Матчи можно сохранять по одному.";
        } + " Сохранить?";
    }

    private static final int HISTORY_WEEKS_BACK = 12;

    private final GroupAccess access;
    private final RulesService rulesService;
    private final MatchRepository matches;
    private final PredictionRepository predictions;
    private final RoundResultRepository results;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final AppTime time;
    private final ScoreDecisionService decisionService;

    public RoundService(GroupAccess access, RulesService rulesService, MatchRepository matches,
                        PredictionRepository predictions, RoundResultRepository results,
                        GroupMemberRepository members, AppUserRepository users, AppTime time,
                        ScoreDecisionService decisionService) {
        this.access = access;
        this.rulesService = rulesService;
        this.matches = matches;
        this.predictions = predictions;
        this.results = results;
        this.members = members;
        this.users = users;
        this.time = time;
        this.decisionService = decisionService;
    }

    // ------------------------------------------------------------------ список туров

    @Transactional(readOnly = true)
    public List<RoundSummary> rounds(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        LocalDate from = time.currentWeek().minusWeeks(HISTORY_WEEKS_BACK);
        LocalDate to = time.lastPredictableWeek();

        Map<LocalDate, List<FootballMatch>> byWeek = new LinkedHashMap<>();
        for (FootballMatch m : matches.findByRoundWeekBetweenOrderByKickoffAtAscIdAsc(from, to)) {
            byWeek.computeIfAbsent(m.getRoundWeek(), k -> new ArrayList<>()).add(m);
        }
        Set<Long> mine = new HashSet<>(predictions.findMatchIdsOfUser(groupId, user.getId(), from, to));
        Set<LocalDate> weeksWithPredictions =
                new HashSet<>(predictions.findWeeksWithPredictions(groupId, from, to));
        Set<LocalDate> finalized = new HashSet<>(results.findFinalizedWeeks(groupId));
        Instant now = Instant.now();

        List<RoundSummary> list = new ArrayList<>();
        for (Map.Entry<LocalDate, List<FootballMatch>> e : byWeek.entrySet()) {
            LocalDate week = e.getKey();
            Rules rules = rulesService.rulesFor(group, week);
            int predicted = 0;
            int open = 0;
            boolean started = false;
            boolean allOver = true;
            for (FootballMatch m : e.getValue()) {
                if (!m.getStatus().isTerminal()) {
                    allOver = false;
                }
                boolean hasMine = mine.contains(m.getId());
                if (hasMine) {
                    predicted++;
                }
                boolean locked = RevealPolicy.locked(now, rules.lockAt(m.getKickoffAt()), m.getStatus());
                if (m.getStatus() == MatchStatus.SCHEDULED && !locked && !hasMine) {
                    open++;
                }
                if (!m.getKickoffAt().isAfter(now) || m.getStatus() == MatchStatus.LIVE
                        || m.getStatus() == MatchStatus.FINISHED) {
                    started = true;
                }
            }
            list.add(new RoundSummary(week,
                    RoundStatus.of(finalized.contains(week), started, allOver,
                            weeksWithPredictions.contains(week)),
                    e.getValue().size(), predicted, open, e.getValue().get(0).getKickoffAt()));
        }
        list.sort(Comparator.comparing(RoundSummary::week).reversed());
        return list;
    }

    // ------------------------------------------------------------------ экран тура

    @Transactional(readOnly = true)
    public RoundView view(AppUser user, Long groupId, LocalDate week) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        requireMonday(week);

        List<FootballMatch> weekMatches = matches.findByRoundWeekOrderByKickoffAtAscIdAsc(week);
        if (weekMatches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "В этом туре нет матчей");
        }

        Rules rules = rulesService.rulesFor(group, week);
        boolean frozen = rulesService.isFrozen(groupId, week);
        List<GroupMember> memberList = members.findAllWithUser(groupId);
        Map<Long, String> names = new HashMap<>();
        for (GroupMember gm : memberList) {
            names.put(gm.getUser().getId(), gm.getUser().getUsername());
        }

        List<Prediction> all = predictions.findForRound(groupId, week);
        Map<Long, List<Prediction>> byMatch = new HashMap<>();
        for (Prediction p : all) {
            byMatch.computeIfAbsent(p.getMatch().getId(), k -> new ArrayList<>()).add(p);
        }

        Long me = user.getId();
        Instant now = Instant.now();
        ScoreBasis basis = rules.scoreBasis();
        Set<Long> predictorIds = new HashSet<>();
        for (Prediction p : all) {
            predictorIds.add(p.getUser().getId());
        }
        Map<Long, ScoreDecisionService.Info> infos =
                decisionService.resolve(groupId, rules, weekMatches, memberList, predictorIds);
        int decisionsForMe = 0;
        List<MatchView> views = new ArrayList<>();
        int open = 0;
        boolean started = false;
        boolean allOver = true;
        for (FootballMatch m : weekMatches) {
            if (!m.getStatus().isTerminal()) {
                allOver = false;
            }
            List<Prediction> forMatch = byMatch.getOrDefault(m.getId(), List.of());
            Prediction mine = null;
            for (Prediction p : forMatch) {
                if (p.getUser().getId().equals(me)) {
                    mine = p;
                }
            }
            Instant lockAt = rules.lockAt(m.getKickoffAt());
            boolean locked = RevealPolicy.locked(now, lockAt, m.getStatus());
            boolean isOpen = m.getStatus() == MatchStatus.SCHEDULED && !locked && mine == null;
            if (isOpen) {
                open++;
            }
            if (!m.getKickoffAt().isAfter(now) || m.getStatus() == MatchStatus.LIVE
                    || m.getStatus() == MatchStatus.FINISHED) {
                started = true;
            }
            ScoreDecisionService.Info info = infos.get(m.getId());
            boolean visible = RevealPolicy.othersVisible(rules.revealMode(), locked, mine != null);

            List<OtherPrediction> others = new ArrayList<>();
            if (visible) {
                List<Prediction> sorted = new ArrayList<>(forMatch);
                sorted.sort(Comparator.comparing(Prediction::getCreatedAt));
                for (Prediction p : sorted) {
                    if (!p.getUser().getId().equals(me)) {
                        others.add(new OtherPrediction(p.getUser().getId(),
                                names.getOrDefault(p.getUser().getId(), "?"),
                                p.getHomeGoals(), p.getAwayGoals(), p.getCreatedAt(), pointsFor(m, p, info, rules.scoring())));
                    }
                }
            }
            MyPrediction myView = mine == null ? null : new MyPrediction(
                    mine.getHomeGoals(), mine.getAwayGoals(), mine.getCreatedAt(), pointsFor(m, mine, info, rules.scoring()));

            ScoreDetail detail = m.getHomeScore() == null ? null : new ScoreDetail(
                    m.getHomeScore(), m.getAwayScore(), m.getExtraHome(), m.getExtraAway(),
                    m.getPenHome(), m.getPenAway());
            ScoreDecisionView decision = decisionService.toView(info, me, ms.isOwner(), names);
            if (decision != null && (decision.canPropose() || decision.canVote())) {
                decisionsForMe++;
            }
            boolean counted = info.kind().counted();
            views.add(new MatchView(m.getId(), m.getHomeTeam(), m.getAwayTeam(), m.getKickoffAt(), lockAt,
                    m.getStage(), m.getMatchday(), m.getStatus(),
                    counted ? m.scoreHome(info.basis()) : null, counted ? m.scoreAway(info.basis()) : null,
                    detail, info.kind().name(), decision, isOpen, locked, forMatch.size(), memberList.size(), myView, visible, others));
        }

        // таблица тура: итоговая, если тур подведён, иначе предварительная по сыгранным матчам
        List<RoundResult> finalRows = results.findByGroupIdAndRoundWeekOrderByPlaceAscUserIdAsc(groupId, week);
        boolean finalized = !finalRows.isEmpty();
        List<StandingRow> standings = new ArrayList<>();
        if (finalized) {
            for (RoundResult r : finalRows) {
                standings.add(new StandingRow(r.getUserId(), names.getOrDefault(r.getUserId(), "?"),
                        r.getPlace(), r.getPoints(), r.getExactScores(), r.getOutcomeHits(),
                        r.getGoalDiffHits(), r.getGoalError(), r.isWinner(), r.isLoser()));
            }
        } else {
            standings = provisional(weekMatches, memberList, all, rules, names, infos);
        }

        List<String> buyers = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        if (finalized) {
            for (StandingRow r : standings) {
                if (r.loser()) {
                    buyers.add(r.username());
                }
                if (r.winner()) {
                    receivers.add(r.username());
                }
            }
        }

        // Кто не сделал ни одного прогноза на тур, в нём не участвует (это видно только в итоговой таблице).
        List<String> notParticipated = new ArrayList<>();
        if (finalized) {
            Set<Long> inTable = new HashSet<>();
            for (StandingRow r : standings) {
                inTable.add(r.userId());
            }
            for (GroupMember gm : memberList) {
                if (!inTable.contains(gm.getUser().getId())) {
                    notParticipated.add(gm.getUser().getUsername());
                }
            }
        }

        boolean canSubmit = open > 0 && !week.isAfter(time.lastPredictableWeek());
        RoundStatus status = RoundStatus.of(finalized, started, allOver, !all.isEmpty());
        String note = status == RoundStatus.NO_PREDICTIONS
                ? "Тур завершён. Никто из участников не делал прогнозов, поэтому итоги не подводились."
                : finalized
                ? "Тур завершён, итоги сохранены в историю. Кто не сделал ни одного прогноза, в туре не участвовал."
                : "Таблица предварительная: по сыгранным матчам, только участники, сделавшие прогнозы. "
                + "Победитель и проигравший определятся по окончании тура.";

        return new RoundView(week, status,
                new RulesDto(rules.lockMinutesBefore(),
                        rules.tiebreakers().stream().map(Enum::name).toList(), rules.revealMode(),
                        basis.extraTime(), basis.penalties(), basis.describe(),
                        rules.submissionMode(), rules.submissionMode().getDescription(),
                        rules.scoring().exactScorePoints(), rules.scoring().goalDiffPoints(),
                        rules.scoring().outcomePoints(), rules.scoring().describe(), frozen),
                views, canSubmit, open, submitWarning(rules.submissionMode()), finalized, standings, buyers, receivers,
                notParticipated, ScoreDecisionService.countPending(infos.values()), decisionsForMe, note);
    }

    private List<StandingRow> provisional(List<FootballMatch> weekMatches, List<GroupMember> memberList,
                                          List<Prediction> all, Rules rules, Map<Long, String> names,
                                          Map<Long, ScoreDecisionService.Info> infos) {
        List<StandingsCalculator.FinishedMatch> finished = new ArrayList<>();
        for (FootballMatch m : weekMatches) {
            ScoreDecisionService.Info info = infos.get(m.getId());
            if (info.kind().counted()) {
                finished.add(new StandingsCalculator.FinishedMatch(m.getId(),
                        m.scoreHome(info.basis()), m.scoreAway(info.basis())));
            }
        }
        if (finished.isEmpty()) {
            return new ArrayList<>();
        }
        List<StandingsCalculator.Guess> guesses = new ArrayList<>();
        Set<Long> predictors = new HashSet<>();
        for (Prediction p : all) {
            guesses.add(new StandingsCalculator.Guess(p.getUser().getId(), p.getMatch().getId(),
                    p.getHomeGoals(), p.getAwayGoals(), p.getCreatedAt()));
            predictors.add(p.getUser().getId());
        }
        List<Long> participants = RoundParticipants.select(toMembers(memberList), predictors);
        List<StandingsCalculator.Row> rows = StandingsCalculator.calculate(
                participants, finished, guesses, rules.tiebreakers(), rules.scoring());

        List<StandingRow> result = new ArrayList<>();
        for (StandingsCalculator.Row r : rows) {
            // победитель и проигравший определяются только по окончании тура
            result.add(new StandingRow(r.userId(), names.getOrDefault(r.userId(), "?"), r.place(),
                    r.points(), r.exactScores(), r.outcomeHits(), r.goalDiffHits(), r.goalError(),
                    false, false));
        }
        return result;
    }

    // ------------------------------------------------------------------ сохранение прогнозов

    /**
     * Сохраняет прогнозы на все открытые матчи тура одной операцией. После сохранения их нельзя
     * изменить: это гарантирует и код, и триггер в базе данных. В этот момент правила тура
     * фиксируются, если до этого в группе никто не сохранял прогноз на этот тур.
     */
    @Transactional
    public SubmitResult submit(AppUser user, Long groupId, LocalDate week, SubmitRequest request) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        requireMonday(week);
        if (week.isAfter(time.lastPredictableWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Приём прогнозов на этот тур ещё не открыт: можно прогнозировать текущую и следующую неделю");
        }

        List<FootballMatch> weekMatches = matches.findByRoundWeekOrderByKickoffAtAscIdAsc(week);
        if (weekMatches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "В этом туре нет матчей");
        }

        // Фиксация правил и сохранение идут в одной транзакции: если проверка ниже не пройдёт,
        // откатится и фиксация.
        rulesService.freezeIfAbsent(group, week);
        Rules rules = rulesService.rulesFor(group, week);

        Long me = user.getId();
        Set<Long> mineIds = new HashSet<>();
        for (Prediction p : predictions.findForRound(groupId, week)) {
            if (p.getUser().getId().equals(me)) {
                mineIds.add(p.getMatch().getId());
            }
        }

        Instant now = Instant.now();
        List<SubmissionValidator.MatchState> states = new ArrayList<>();
        Map<Long, FootballMatch> byId = new HashMap<>();
        for (FootballMatch m : weekMatches) {
            byId.put(m.getId(), m);
            states.add(new SubmissionValidator.MatchState(m.getId(), m.getHomeTeam() + " — " + m.getAwayTeam(),
                    m.getStatus(), rules.lockAt(m.getKickoffAt()), mineIds.contains(m.getId()),
                    m.getKickoffAt().atZone(time.zone()).toLocalDate()));
        }
        List<SubmissionValidator.Entry> entries = new ArrayList<>();
        for (Entry e : request.predictions()) {
            entries.add(new SubmissionValidator.Entry(e.matchId(), e.homeGoals(), e.awayGoals()));
        }

        try {
            SubmissionValidator.validate(now, Boolean.TRUE.equals(request.confirmed()), states, entries,
                    rules.submissionMode());
        } catch (SubmissionException ex) {
            throw new ResponseStatusException(HttpStatus.valueOf(ex.status()), ex.getMessage());
        }

        AppUser userRef = users.getReferenceById(me);
        List<Prediction> toSave = new ArrayList<>();
        for (SubmissionValidator.Entry e : entries) {
            toSave.add(new Prediction(group, userRef, byId.get(e.matchId()), e.homeGoals(), e.awayGoals(), now));
        }
        try {
            predictions.saveAll(toSave);
            predictions.flush();
        } catch (DataIntegrityViolationException ex) {
            // например, двойное нажатие «Сохранить»
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Прогнозы уже сохранены. Обновите экран тура.");
        }
        return new SubmitResult(toSave.size(), now,
                "Прогнозы сохранены. Теперь вы видите прогнозы других участников на эти матчи.");
    }

    // ------------------------------------------------------------------ вспомогательное

    private static Integer pointsFor(FootballMatch m, Prediction p, ScoreDecisionService.Info info,
                                     Scoring scoring) {
        if (!info.kind().counted()) {
            return null;
        }
        return scoring.points(p.getHomeGoals(), p.getAwayGoals(),
                m.scoreHome(info.basis()), m.scoreAway(info.basis()));
    }

    static List<RoundParticipants.Member> toMembers(List<GroupMember> list) {
        List<RoundParticipants.Member> result = new ArrayList<>();
        for (GroupMember gm : list) {
            result.add(new RoundParticipants.Member(gm.getUser().getId(), gm.getJoinedAt()));
        }
        return result;
    }

    private static void requireMonday(LocalDate week) {
        if (!RoundWeeks.mondayOf(week).equals(week)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Тур обозначается датой понедельника, например 2026-11-02");
        }
    }
}
