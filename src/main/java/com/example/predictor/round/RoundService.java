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
import com.example.predictor.common.UserLabel;
import com.example.predictor.names.TeamNameMap;
import com.example.predictor.names.TeamNamesService;
import com.example.predictor.repository.AppUserRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.round.RoundDtos.*;
import com.example.predictor.round.SubmissionValidator.SubmissionException;
import com.example.predictor.scoring.Scoring;
import com.example.predictor.standings.StandingsCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    /** Прошедшие туры идут после будущих: сдвиг больше любого расстояния в днях. */
    private static final long PAST_OFFSET = 1_000_000L;

    private final GroupAccess access;
    private final RulesService rulesService;
    private final PredictionRepository predictions;
    private final RoundResultRepository results;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final AppTime time;
    private final ScoreDecisionService decisionService;
    private final RoundMatches roundMatches;
    private final TeamNamesService teamNames;
    private final JokerService jokerService;
    private final LeaderService leaderService;
    private final com.example.predictor.repository.GroupRoundRepository groupRounds;
    private final com.example.predictor.repository.StandingRowRepository standingRows;
    private final com.example.predictor.repository.HeadToHeadRepository headToHead;
    private final com.example.predictor.feed.TeamFormService teamForm;

    public RoundService(GroupAccess access, RulesService rulesService,
                        PredictionRepository predictions, RoundResultRepository results,
                        GroupMemberRepository members, AppUserRepository users, AppTime time,
                        ScoreDecisionService decisionService, RoundMatches roundMatches,
                        JokerService jokerService, LeaderService leaderService,
                        TeamNamesService teamNames,
                        com.example.predictor.repository.GroupRoundRepository groupRounds,
                        com.example.predictor.repository.StandingRowRepository standingRows,
                        com.example.predictor.repository.HeadToHeadRepository headToHead,
                        com.example.predictor.feed.TeamFormService teamForm) {
        this.teamForm = teamForm;
        this.groupRounds = groupRounds;
        this.standingRows = standingRows;
        this.headToHead = headToHead;
        this.teamNames = teamNames;
        this.roundMatches = roundMatches;
        this.jokerService = jokerService;
        this.leaderService = leaderService;
        this.access = access;
        this.rulesService = rulesService;
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
        // собирать тур может не каждый: без этого права кнопка выбора матчей не нужна
        boolean canSelect = GroupAccess.canSelectMatches(ms);
        LocalDate from = time.currentWeek().minusWeeks(HISTORY_WEEKS_BACK);
        LocalDate to = time.lastPredictableWeek();
        // туры ищем по ключам: у второго тура недели ключ — день после понедельника
        LocalDate keysTo = time.lastRoundKey();

        // в тур входят только матчи, отобранные владельцем группы
        Map<LocalDate, List<FootballMatch>> byWeek = roundMatches.forRange(groupId, from, keysTo);
        Set<Long> mine = new HashSet<>(predictions.findMatchIdsOfUser(groupId, user.getId(), from, keysTo));
        Set<LocalDate> weeksWithPredictions =
                new HashSet<>(predictions.findWeeksWithPredictions(groupId, from, keysTo));
        Set<LocalDate> finalized = new HashSet<>(results.findFinalizedWeeks(groupId));
        Instant now = Instant.now();

        // кто на какие матчи уже сделал прогноз: по этому видно, все ли готовы к туру
        List<GroupMember> memberList = members.findAllWithUser(groupId);
        Map<Long, Set<Long>> matchesByUser = new HashMap<>();
        for (Object[] pair : predictions.findUserMatchPairs(groupId, from, keysTo)) {
            matchesByUser.computeIfAbsent((Long) pair[0], k -> new HashSet<>()).add((Long) pair[1]);
        }

        // названия и режим туров: их задают те, кто собирает состав
        Map<LocalDate, String> titles = new HashMap<>();
        Set<LocalDate> friendly = new HashSet<>();
        for (com.example.predictor.domain.GroupRound round
                : groupRounds.findByGroupIdAndRoundKeyBetweenOrderByRoundKeyAsc(groupId, from,
                keysTo)) {
            titles.put(round.getRoundKey(), round.getTitle());
            if (round.isFriendly()) {
                friendly.add(round.getRoundKey());
            }
        }

        // кто выиграл сыгранные туры: в списке это видно сразу, без захода в тур
        Map<Long, String> memberNames = new HashMap<>();
        for (GroupMember gm : memberList) {
            memberNames.put(gm.getUser().getId(), UserLabel.of(gm.getUser()).username());
        }
        Map<LocalDate, List<String>> winners = new HashMap<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            if (r.isWinner()) {
                winners.computeIfAbsent(r.getRoundWeek(), k -> new ArrayList<>())
                        .add(memberNames.getOrDefault(r.getUserId(), "участник"));
            }
        }

        List<RoundSummary> list = new ArrayList<>();
        for (Map.Entry<LocalDate, List<FootballMatch>> e : byWeek.entrySet()) {
            LocalDate week = e.getKey();
            Rules rules = rulesService.rulesFor(group, week);
            int predicted = 0;
            int open = 0;
            boolean started = false;
            boolean allOver = true;
            // турниры тура: в одном туре их бывает несколько, поэтому список, а не один
            List<String> competitions = new ArrayList<>();
            for (FootballMatch m : e.getValue()) {
                String label = m.getCompetition() == null ? null
                        : m.getCompetition().getShortName();
                if (label != null && !label.isBlank() && !competitions.contains(label)) {
                    competitions.add(label);
                }
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
            // участник готов к туру, если у него есть прогноз на каждый неотменённый матч
            Set<Long> playable = new HashSet<>();
            for (FootballMatch m : e.getValue()) {
                if (m.getStatus() != MatchStatus.CANCELLED && m.getStatus() != MatchStatus.POSTPONED) {
                    playable.add(m.getId());
                }
            }
            int ready = 0;
            for (GroupMember gm : memberList) {
                Set<Long> his = matchesByUser.get(gm.getUser().getId());
                if (!playable.isEmpty() && his != null && his.containsAll(playable)) {
                    ready++;
                }
            }
            Instant lastKickoff = e.getValue().get(e.getValue().size() - 1).getKickoffAt();
            list.add(new RoundSummary(week,
                    RoundStatus.of(finalized.contains(week), started, allOver,
                            weeksWithPredictions.contains(week)),
                    e.getValue().size(), predicted, open, e.getValue().get(0).getKickoffAt(),
                    !RoundKeys.weekOf(week).isBefore(time.currentWeek())
                            && !RoundKeys.weekOf(week).isAfter(to),
                    ready, memberList.size(), canSelect,
                    RoundKeys.noOf(week), titles.get(week), lastKickoff,
                    friendly.contains(week), competitions, winnerOf(winners.get(week))));
        }

        // Текущая и следующая недели показываются всегда, даже если владелец ещё не выбрал матчи:
        // иначе ему некуда нажать, чтобы их выбрать.
        for (LocalDate week = time.currentWeek(); !week.isAfter(to); week = week.plusWeeks(1)) {
            if (!byWeek.containsKey(week)) {
                list.add(new RoundSummary(week, RoundStatus.UPCOMING, 0, 0, 0, null, true,
                        0, memberList.size(), canSelect, 1, titles.get(week), null,
                        friendly.contains(week), List.of(), null));
            }
        }
        // Ближайший тур — сверху: сначала текущая неделя и будущие по возрастанию,
        // следом прошедшие, начиная с недавней. Раньше список шёл от дальнего будущего,
        // и тур, на который как раз делают прогнозы, уезжал за нижний край экрана.
        LocalDate current = time.currentWeek();
        list.sort(Comparator.comparingLong((RoundSummary r) -> distanceKey(r.week(), current)));
        return list;
    }

    /** Победитель тура одной строкой: обычно один, но при равенстве баллов бывает и двое. */
    private static String winnerOf(List<String> names) {
        return names == null || names.isEmpty() ? null : String.join(", ", names);
    }

    /**
     * Расстояние тура от текущей недели для сортировки: будущее идёт по возрастанию,
     * прошедшее — следом, начиная с самого недавнего.
     */
    static long distanceKey(LocalDate week, LocalDate current) {
        long days = ChronoUnit.DAYS.between(current, week);
        return days >= 0 ? days : PAST_OFFSET - days;
    }

    // ------------------------------------------------------------------ экран тура

    @Transactional(readOnly = true)
    public RoundView view(AppUser user, Long groupId, LocalDate week) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        requireRoundKey(week);

        List<FootballMatch> weekMatches = roundMatches.forRound(groupId, week);
        if (weekMatches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "В этом туре пока нет матчей: владелец группы ещё не выбрал их");
        }

        Rules rules = rulesService.rulesFor(group, week);
        boolean frozen = rulesService.isFrozen(groupId, week);
        TeamNameMap teamLabels = teamNames.mapFor(group);
        List<GroupMember> memberList = members.findAllWithUser(groupId);
        Map<Long, UserLabel> names = new HashMap<>();
        // для блока голосования по спорному счёту хватит имени одной строкой
        Map<Long, String> plainNames = new HashMap<>();
        for (GroupMember gm : memberList) {
            UserLabel label = UserLabel.of(gm.getUser());
            names.put(gm.getUser().getId(), label);
            plainNames.put(gm.getUser().getId(), label.display());
        }

        // джокеры тура: userId → matchId. Баллы за этот матч считаются вдвойне
        Map<Long, Long> jokers = jokerService.forRound(groupId, week);
        // майки: жёлтая у лидера сезона, красная у победителя последнего тура
        LeaderService.Jerseys jerseys = leaderService.jerseys(groupId);

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
        // справки от источника: форма из турнирной таблицы и личные встречи
        Map<String, String> forms = formsOf(weekMatches);
        Map<Long, com.example.predictor.domain.MatchHeadToHead> h2h = new HashMap<>();
        for (com.example.predictor.domain.MatchHeadToHead row
                : headToHead.findByMatchIdIn(weekMatches.stream().map(FootballMatch::getId).toList())) {
            h2h.put(row.getMatchId(), row);
        }

        List<MatchView> views = new ArrayList<>();
        int open = 0;
        int liveMatches = 0;
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
                        UserLabel who = names.getOrDefault(p.getUser().getId(), UserLabel.UNKNOWN);
                        boolean hisJoker = m.getId().equals(jokers.get(p.getUser().getId()));
                        others.add(new OtherPrediction(p.getUser().getId(), who.username(), who.badge(),
                                p.getHomeGoals(), p.getAwayGoals(), p.getCreatedAt(),
                                pointsFor(m, p, info, rules.scoring(), hisJoker), hisJoker,
                                jerseys.leader(p.getUser().getId()),
                                jerseys.lastWinner(p.getUser().getId())));
                    }
                }
            }
            MyPrediction myView = mine == null ? null : new MyPrediction(
                    mine.getHomeGoals(), mine.getAwayGoals(), mine.getCreatedAt(),
                    pointsFor(m, mine, info, rules.scoring(), m.getId().equals(jokers.get(me))));

            ScoreDetail detail = m.getHomeScore() == null ? null : new ScoreDetail(
                    m.getHomeScore(), m.getAwayScore(), m.getExtraHome(), m.getExtraAway(),
                    m.getPenHome(), m.getPenAway());
            ScoreDecisionView decision = decisionService.toView(info, me, ms.isOwner(), plainNames);
            if (decision != null && (decision.canPropose() || decision.canVote())) {
                decisionsForMe++;
            }
            boolean counted = info.kind().counted();
            if (m.getStatus() == MatchStatus.LIVE && m.getLiveHome() != null) {
                liveMatches++;
            }
            views.add(new MatchView(m.getId(), teamLabels.display(m.getHomeTeam()),
                    teamLabels.display(m.getAwayTeam()),
                    m.getCompetition() == null ? null : m.getCompetition().getCode(),
                    m.getCompetition() == null ? null : m.getCompetition().getShortName(),
                    m.getHomeCrest(), m.getAwayCrest(),
                    m.getKickoffAt(), lockAt,
                    StageLabels.of(m.getStage()), m.getMatchday(), m.getStatus(),
                    counted ? m.scoreHome(info.basis()) : null, counted ? m.scoreAway(info.basis()) : null,
                    m.getLiveHome(), m.getLiveAway(),
                    detail, summaryOf(forMatch, mine, visible), info.kind().name(), decision, isOpen, locked, forMatch.size(), memberList.size(), myView, visible, others,
                    forms.get(m.getHomeTeam()), forms.get(m.getAwayTeam()),
                    headToHeadOf(h2h.get(m.getId()))));
        }

        // таблица тура: итоговая, если тур подведён, иначе предварительная по сыгранным матчам
        List<RoundResult> finalRows = results.findByGroupIdAndRoundWeekOrderByPlaceAscUserIdAsc(groupId, week);
        boolean finalized = !finalRows.isEmpty();
        List<StandingRow> standings = new ArrayList<>();
        // Чем разводили равные баллы: без этого двое проигравших выглядят поломкой, хотя
        // это просто совпали и баллы, и все показатели группы.
        List<TiebreakNotes.Score> scores = new ArrayList<>();
        if (finalized) {
            // показатели берём из сохранённых итогов: тур подведён по правилам своего времени
            for (RoundResult r : finalRows) {
                scores.add(new TiebreakNotes.Score(r.getUserId(), r.getPoints(),
                        r.getExactScores(), r.getGoalDiffHits(), r.getGoalError(),
                        r.getSubmittedAt()));
            }
            Map<Long, String> tiebreaks = TiebreakNotes.rowNotes(scores, rules.tiebreakers());
            for (RoundResult r : finalRows) {
                UserLabel who = names.getOrDefault(r.getUserId(), UserLabel.UNKNOWN);
                standings.add(new StandingRow(r.getUserId(), who.username(), who.badge(),
                        r.getPlace(), r.getPoints(), 0, r.getExactScores(), r.getOutcomeHits(),
                        r.getGoalDiffHits(), r.getGoalError(), r.isWinner(), r.isLoser(),
                        jerseys.leader(r.getUserId()), jerseys.lastWinner(r.getUserId()),
                        tiebreaks.get(r.getUserId())));
            }
        } else {
            standings = provisional(weekMatches, memberList, all, rules, names, infos, jokers,
                    jerseys, scores);
        }
        String tiebreakNote = TiebreakNotes.note(scores, rules.tiebreakers());

        boolean friendly = groupRounds.findByGroupIdAndRoundKey(groupId, week)
                .map(com.example.predictor.domain.GroupRound::isFriendly).orElse(false);

        // в товарищеском туре победитель есть, а шаурмы нет: списки «кто кормит» пусты
        List<String> buyers = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        if (finalized && !friendly) {
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

        boolean canSubmit = open > 0 && !RoundKeys.weekOf(week).isAfter(time.lastPredictableWeek());
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
                notParticipated, ScoreDecisionService.countPending(infos.values()), decisionsForMe, note,
                group.isJokerEnabled(), jokers.get(me), liveMatches,
                RoundKeys.noOf(week),
                groupRounds.findByGroupIdAndRoundKey(groupId, week)
                        .map(com.example.predictor.domain.GroupRound::getTitle).orElse(null),
                weekMatches.get(0).getKickoffAt(),
                weekMatches.get(weekMatches.size() - 1).getKickoffAt(),
                friendly, tiebreakNote,
                // пересчёт нужен, только когда итоги уже подведены, и только владельцу
                finalized && ms.isOwner());
    }

    private List<StandingRow> provisional(List<FootballMatch> weekMatches, List<GroupMember> memberList,
                                          List<Prediction> all, Rules rules, Map<Long, UserLabel> names,
                                          Map<Long, ScoreDecisionService.Info> infos,
                                          Map<Long, Long> jokers, LeaderService.Jerseys jerseys,
                                          List<TiebreakNotes.Score> scoresOut) {
        List<StandingsCalculator.FinishedMatch> finished = new ArrayList<>();
        // идущие матчи считаем отдельно: по ним видно, что будет, «если так и закончится»
        List<StandingsCalculator.FinishedMatch> live = new ArrayList<>();
        for (FootballMatch m : weekMatches) {
            ScoreDecisionService.Info info = infos.get(m.getId());
            if (info.kind().counted()) {
                finished.add(new StandingsCalculator.FinishedMatch(m.getId(),
                        m.scoreHome(info.basis()), m.scoreAway(info.basis())));
            } else if (m.getStatus() == MatchStatus.LIVE && m.getLiveHome() != null
                    && m.getLiveAway() != null) {
                live.add(new StandingsCalculator.FinishedMatch(m.getId(),
                        m.getLiveHome(), m.getLiveAway()));
            }
        }
        if (finished.isEmpty() && live.isEmpty()) {
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
        // баллы за сыгранное
        Map<Long, Integer> settled = new HashMap<>();
        for (StandingsCalculator.Row r : StandingsCalculator.calculate(
                participants, finished, guesses, rules.tiebreakers(), rules.scoring(), jokers)) {
            settled.put(r.userId(), r.points());
        }
        // места считаем с учётом идущих матчей: таблица отвечает на вопрос «а если так и закончится»
        List<StandingsCalculator.FinishedMatch> withLive = new ArrayList<>(finished);
        withLive.addAll(live);
        List<StandingsCalculator.Row> rows = StandingsCalculator.calculate(
                participants, withLive, guesses, rules.tiebreakers(), rules.scoring(), jokers);

        // показатели тех, кто сравнялся по баллам: по ним и расставлены места
        for (StandingsCalculator.Row r : rows) {
            scoresOut.add(new TiebreakNotes.Score(r.userId(), r.points(), r.exactScores(),
                    r.goalDiffHits(), r.goalError(), r.submittedAt()));
        }
        Map<Long, String> tiebreaks = TiebreakNotes.rowNotes(scoresOut, rules.tiebreakers());

        List<StandingRow> result = new ArrayList<>();
        for (StandingsCalculator.Row r : rows) {
            // победитель и проигравший определяются только по окончании тура
            UserLabel who = names.getOrDefault(r.userId(), UserLabel.UNKNOWN);
            int done = settled.getOrDefault(r.userId(), 0);
            result.add(new StandingRow(r.userId(), who.username(), who.badge(), r.place(),
                    done, r.points() - done, r.exactScores(), r.outcomeHits(), r.goalDiffHits(),
                    r.goalError(), false, false, jerseys.leader(r.userId()),
                    jerseys.lastWinner(r.userId()),
                    tiebreaks.get(r.userId())));
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
        requireRoundKey(week);
        if (RoundKeys.weekOf(week).isAfter(time.lastPredictableWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Приём прогнозов на этот тур ещё не открыт: можно прогнозировать текущую и следующую неделю");
        }

        List<FootballMatch> weekMatches = roundMatches.forRound(groupId, week);
        if (weekMatches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "В этом туре пока нет матчей: владелец группы ещё не выбрал их");
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
        TeamNameMap teamLabels = teamNames.mapFor(group);
        List<SubmissionValidator.MatchState> states = new ArrayList<>();
        Map<Long, FootballMatch> byId = new HashMap<>();
        for (FootballMatch m : weekMatches) {
            byId.put(m.getId(), m);
            states.add(new SubmissionValidator.MatchState(m.getId(),
                    teamLabels.display(m.getHomeTeam()) + " — " + teamLabels.display(m.getAwayTeam()),
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

    /**
     * Одной строкой: как поставили остальные. Появляется, когда чужие прогнозы уже открыты,
     * — иначе это была бы подсказка.
     */
    private static String summaryOf(List<Prediction> forMatch, Prediction mine, boolean visible) {
        if (!visible || forMatch.size() < 2) {
            return null;
        }
        Map<String, Integer> byScore = new LinkedHashMap<>();
        for (Prediction p : forMatch) {
            byScore.merge(p.getHomeGoals() + ":" + p.getAwayGoals(), 1, Integer::sum);
        }
        String popular = null;
        int popularCount = 0;
        for (Map.Entry<String, Integer> e : byScore.entrySet()) {
            if (e.getValue() > popularCount) {
                popular = e.getKey();
                popularCount = e.getValue();
            }
        }
        StringBuilder text = new StringBuilder();
        if (popularCount > 1) {
            text.append(popularCount).append(" из ").append(forMatch.size())
                    .append(" поставили ").append(popular);
        } else {
            text.append("Все поставили по-разному");
        }
        if (mine != null) {
            String my = mine.getHomeGoals() + ":" + mine.getAwayGoals();
            if (byScore.getOrDefault(my, 0) == 1) {
                text.append(forMatch.size() > 1 ? ", вы единственный с " + my : "");
            }
        }
        return text.toString();
    }

    private static Integer pointsFor(FootballMatch m, Prediction p, ScoreDecisionService.Info info,
                                     Scoring scoring, boolean joker) {
        if (!info.kind().counted()) {
            return null;
        }
        int points = scoring.points(p.getHomeGoals(), p.getAwayGoals(),
                m.scoreHome(info.basis()), m.scoreAway(info.basis()));
        return joker ? points * 2 : points;
    }

    static List<RoundParticipants.Member> toMembers(List<GroupMember> list) {
        List<RoundParticipants.Member> result = new ArrayList<>();
        for (GroupMember gm : list) {
            result.add(new RoundParticipants.Member(gm.getUser().getId(), gm.getJoinedAt()));
        }
        return result;
    }

    /** Форма команд из турнирных таблиц: название команды → «В В Н П В». */
    private Map<String, String> formsOf(List<FootballMatch> weekMatches) {
        Set<Long> competitionIds = new HashSet<>();
        for (FootballMatch m : weekMatches) {
            if (m.getCompetition() != null) {
                competitionIds.add(m.getCompetition().getId());
            }
        }
        Map<String, String> forms = new HashMap<>();
        if (!competitionIds.isEmpty()) {
            for (com.example.predictor.domain.StandingRowEntity row
                    : standingRows.findByCompetitionIdIn(new ArrayList<>(competitionIds))) {
                String form = com.example.predictor.feed.StandingsService.form(row.getForm());
                if (form != null) {
                    forms.put(row.getTeamName(), form);
                }
            }
        }
        // чего источник не прислал, считаем сами по сыгранным матчам
        Set<String> missing = new java.util.LinkedHashSet<>();
        for (FootballMatch m : weekMatches) {
            if (!forms.containsKey(m.getHomeTeam())) {
                missing.add(m.getHomeTeam());
            }
            if (!forms.containsKey(m.getAwayTeam())) {
                missing.add(m.getAwayTeam());
            }
        }
        forms.putAll(teamForm.formOf(missing));
        return forms;
    }

    private static RoundDtos.HeadToHeadView headToHeadOf(
            com.example.predictor.domain.MatchHeadToHead row) {
        if (row == null || row.getTotal() <= 0) {
            return null;
        }
        return new RoundDtos.HeadToHeadView(row.getTotal(), row.getHomeWins(), row.getDraws(),
                row.getAwayWins());
    }

    /**
     * Ключ тура — дата. Обычно это понедельник недели, но если в неделе несколько туров,
     * второй и следующие обозначаются ближайшими днями той же недели.
     */
    private static void requireRoundKey(LocalDate week) {
        if (week == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тур не указан");
        }
    }
}
