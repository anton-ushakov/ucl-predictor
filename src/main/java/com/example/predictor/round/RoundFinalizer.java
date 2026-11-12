package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.Prediction;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.Tiebreaker;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.GroupRound;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.standings.StandingsCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Подводит итоги туров. Тур группы считается завершённым, когда все его матчи сыграны или отменены.
 * Итоги записываются один раз и больше не меняются: если позже поменять настройки группы или
 * football-data.org поправит счёт, история прошлых туров останется прежней.
 */
@Service
public class RoundFinalizer {

    private static final Logger log = LoggerFactory.getLogger(RoundFinalizer.class);

    private final PredictionRepository predictions;
    private final MatchRepository matches;
    private final RoundResultRepository results;
    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final RulesService rulesService;
    private final AppTime time;
    private final TransactionTemplate tx;
    private final ScoreDecisionService decisionService;

    public RoundFinalizer(PredictionRepository predictions, MatchRepository matches,
                          RoundResultRepository results, FriendGroupRepository groups,
                          GroupMemberRepository members, RulesService rulesService, AppTime time,
                          PlatformTransactionManager transactionManager,
                          ScoreDecisionService decisionService) {
        this.predictions = predictions;
        this.matches = matches;
        this.results = results;
        this.groups = groups;
        this.members = members;
        this.rulesService = rulesService;
        this.time = time;
        this.tx = new TransactionTemplate(transactionManager);
        this.decisionService = decisionService;
    }

    /** Подводит итоги всех туров, которые уже можно подвести. Возвращает число подведённых туров. */
    public int finalizeReadyRounds() {
        LocalDate currentWeek = time.currentWeek();
        int done = 0;
        for (GroupRound gr : predictions.findGroupRounds()) {
            if (gr.roundWeek().isAfter(currentWeek)) {
                continue;
            }
            if (results.existsByGroupIdAndRoundWeek(gr.groupId(), gr.roundWeek())) {
                continue;
            }
            try {
                Boolean finalized = tx.execute(status -> finalizeRound(gr.groupId(), gr.roundWeek()));
                if (Boolean.TRUE.equals(finalized)) {
                    done++;
                    log.info("Подведены итоги тура {} для группы {}", gr.roundWeek(), gr.groupId());
                }
            } catch (RuntimeException e) {
                log.warn("Не удалось подвести итоги тура {} группы {}: {}",
                        gr.roundWeek(), gr.groupId(), e.getMessage());
            }
        }
        return done;
    }

    private boolean finalizeRound(Long groupId, LocalDate week) {
        FriendGroup group = groups.findById(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        List<FootballMatch> weekMatches = matches.findByRoundWeekOrderByKickoffAtAscIdAsc(week);
        if (weekMatches.isEmpty()) {
            return false;
        }
        Rules rules = rulesService.rulesFor(group, week);
        List<GroupMember> memberList = members.findAllWithUser(groupId);
        List<Prediction> all = predictions.findForRound(groupId, week);
        Set<Long> predictors = new HashSet<>();
        for (Prediction p : all) {
            predictors.add(p.getUser().getId());
        }

        // Тур подводится, когда по каждому матчу всё решено: сыгран, отменён, а если не хватает данных
        // о добавочном времени или пенальти, то владелец и участники уже приняли решение.
        Map<Long, ScoreDecisionService.Info> infos =
                decisionService.resolve(groupId, rules, weekMatches, memberList, predictors);
        List<StandingsCalculator.FinishedMatch> finished = new ArrayList<>();
        for (FootballMatch m : weekMatches) {
            ScoreDecisionService.Info info = infos.get(m.getId());
            if (!info.kind().settled()) {
                return false;
            }
            if (info.kind().counted()) {
                finished.add(new StandingsCalculator.FinishedMatch(m.getId(),
                        m.scoreHome(info.basis()), m.scoreAway(info.basis())));
            }
        }
        if (finished.isEmpty()) {
            return false; // все матчи отменены или не учитываются: считать нечего
        }

        List<StandingsCalculator.Guess> guesses = new ArrayList<>();
        for (Prediction p : all) {
            guesses.add(new StandingsCalculator.Guess(p.getUser().getId(), p.getMatch().getId(),
                    p.getHomeGoals(), p.getAwayGoals(), p.getCreatedAt()));
        }
        // участвуют только те, кто сохранил хотя бы один прогноз на тур
        List<Long> participants = RoundParticipants.select(RoundService.toMembers(memberList), predictors);
        if (participants.isEmpty()) {
            return false;
        }

        List<StandingsCalculator.Row> rows = StandingsCalculator.calculate(
                participants, finished, guesses, rules.tiebreakers());

        String tiebreakersCsv = Tiebreaker.toCsv(rules.tiebreakers());
        Instant now = Instant.now();
        List<RoundResult> toSave = new ArrayList<>();
        for (StandingsCalculator.Row r : rows) {
            toSave.add(new RoundResult(groupId, week, r.userId(), r.points(), r.exactScores(),
                    r.outcomeHits(), r.goalDiffHits(), r.goalError(), r.submittedAt(), r.place(),
                    r.winner(), r.loser(), tiebreakersCsv, rules.scoreBasis(), now));
        }
        results.saveAll(toSave);
        return true;
    }
}
