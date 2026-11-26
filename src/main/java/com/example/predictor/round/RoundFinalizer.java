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
import com.example.predictor.repository.GroupRoundKey;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.repository.ShawarmaDebtRepository;
import org.springframework.beans.factory.annotation.Value;
import com.example.predictor.standings.StandingsCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Подводит итоги туров. Тур группы считается завершённым, когда все его матчи сыграны или отменены.
 *
 * Итоги считаются по правилам, замороженным на начало тура: поменять настройки группы задним
 * числом и переписать этим прошлые туры нельзя. А вот счёт — другое дело. Источник иногда
 * отдаёт матч сыгранным раньше, чем окончательный счёт, и правит его потом: отменённый гол,
 * уточнённый автогол. Поэтому, во-первых, итоги подводятся не сразу после финального свистка,
 * а когда счёт успел устояться; во-вторых, если счёт всё же поправили, итоги пересчитываются —
 * сами или по кнопке владельца.
 */
@Service
public class RoundFinalizer {

    private static final Logger log = LoggerFactory.getLogger(RoundFinalizer.class);

    private final PredictionRepository predictions;
    private final RoundMatches roundMatches;
    private final RoundResultRepository results;
    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final RulesService rulesService;
    private final AppTime time;
    private final TransactionTemplate tx;
    private final ScoreDecisionService decisionService;
    private final DebtService debtService;
    private final com.example.predictor.repository.GroupRoundRepository groupRounds;
    private final JokerService jokerService;
    private final ShawarmaDebtRepository debts;
    private final ExhibitionService exhibition;

    public RoundFinalizer(PredictionRepository predictions, RoundMatches roundMatches,
                          RoundResultRepository results, FriendGroupRepository groups,
                          GroupMemberRepository members, RulesService rulesService, AppTime time,
                          PlatformTransactionManager transactionManager,
                          ScoreDecisionService decisionService, DebtService debtService,
                          JokerService jokerService,
                          com.example.predictor.repository.GroupRoundRepository groupRounds,
                          ShawarmaDebtRepository debts, ExhibitionService exhibition,
                          @Value("${app.rounds.settle-delay:PT20M}") Duration settleDelay) {
        this.exhibition = exhibition;
        this.debts = debts;
        this.settleDelay = settleDelay;
        this.groupRounds = groupRounds;
        this.jokerService = jokerService;
        this.debtService = debtService;
        this.predictions = predictions;
        this.roundMatches = roundMatches;
        this.results = results;
        this.groups = groups;
        this.members = members;
        this.rulesService = rulesService;
        this.time = time;
        this.tx = new TransactionTemplate(transactionManager);
        this.decisionService = decisionService;
    }

    /**
     * Сколько ждать после того, как счёт перестал меняться. Пауза короткая: она нужна лишь
     * на случай, когда источник объявил матч сыгранным, а протокол ещё правит.
     */
    private final Duration settleDelay;

    /**
     * Запасная пауза — от начала матча, если момент окончательного счёта неизвестен (матч
     * сыгран давно, ещё до появления этой отметки). Берём с запасом на добавочное время и
     * серию пенальти: с ними матч идёт дольше трёх часов.
     */
    private static final Duration LONG_WAIT = Duration.ofMinutes(230);

    /** Подводит итоги всех туров, которые уже можно подвести. Возвращает число подведённых туров. */
    public int finalizeReadyRounds() {
        LocalDate today = LocalDate.now(time.zone());
        int done = 0;
        for (GroupRoundKey gr : predictions.findGroupRounds()) {
            // ключ тура — служебная дата, и будущий тур подводить нечего: матчи ещё не сыграны
            if (gr.roundWeek().isAfter(today.plusDays(1))) {
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

    /**
     * Пересчитывает итоги уже подведённого тура по текущему счёту.
     *
     * Правила тура остаются прежними — они заморожены, и менять их задним числом нельзя.
     * Меняется только счёт: источник поправил протокол, владелец уточнил добавочное время.
     * Отданные долги не трогаем: шаурма, которую уже съели, не отменяется. А вот неотданные
     * заводятся заново по новым итогам.
     *
     * @return true, если итоги пересчитаны. false — если по туру сейчас вообще нельзя
     *         подвести итоги; тогда прежние итоги остаются на месте.
     */
    public boolean recount(Long groupId, LocalDate roundKey) {
        Boolean done = tx.execute(status -> {
            results.deleteByGroupIdAndRoundWeek(groupId, roundKey);
            results.flush();
            for (var debt : debts.findByGroupIdAndRoundWeekAndSettledAtIsNull(groupId, roundKey)) {
                debts.delete(debt);
            }
            if (!finalizeRound(groupId, roundKey)) {
                // считать нечего — возвращаем всё как было
                status.setRollbackOnly();
                return false;
            }
            return true;
        });
        if (Boolean.TRUE.equals(done)) {
            log.info("Итоги тура {} группы {} пересчитаны", roundKey, groupId);
        }
        return Boolean.TRUE.equals(done);
    }

    /**
     * С какого момента счёт матча можно считать окончательным.
     *
     * Отсчитываем от времени, когда счёт перестал меняться, а не от начала матча: матч с
     * добавочным временем и серией пенальти идёт дольше трёх часов, и фиксированная пауза
     * от начала закрыла бы тур по счёту основного времени.
     */
    private Instant readyAt(FootballMatch m) {
        if (m.getSettledAt() != null) {
            return m.getSettledAt().plus(settleDelay);
        }
        return m.getKickoffAt() == null ? Instant.EPOCH : m.getKickoffAt().plus(LONG_WAIT);
    }

    private boolean finalizeRound(Long groupId, LocalDate week) {
        FriendGroup group = groups.findById(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        // тур состоит только из матчей, отобранных владельцем группы
        List<FootballMatch> weekMatches = roundMatches.forRound(groupId, week);
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
        Instant now = Instant.now();
        List<StandingsCalculator.FinishedMatch> finished = new ArrayList<>();
        for (FootballMatch m : weekMatches) {
            ScoreDecisionService.Info info = infos.get(m.getId());
            if (!info.kind().settled()) {
                return false;
            }
            if (info.kind().counted()) {
                // Счёт должен устояться: источник бывает отдаёт матч сыгранным, пока протокол
                // ещё правят, и подведённые по такому счёту итоги оказываются чужими.
                if (now.isBefore(readyAt(m))) {
                    return false;
                }
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

        // кто играет вне конкурса: баллы им считаем, победителя и проигравшего — без них
        Set<Long> outOfCompetition = exhibition.of(groupId, week);
        List<StandingsCalculator.Row> rows = StandingsCalculator.calculate(
                participants, finished, guesses, rules.tiebreakers(), rules.scoring(),
                jokerService.forRound(groupId, week), outOfCompetition);

        String tiebreakersCsv = Tiebreaker.toCsv(rules.tiebreakers());
        List<RoundResult> toSave = new ArrayList<>();
        for (StandingsCalculator.Row r : rows) {
            toSave.add(new RoundResult(groupId, week, r.userId(), r.points(), r.exactScores(),
                    r.outcomeHits(), r.goalDiffHits(), r.goalError(), r.submittedAt(), r.place(),
                    r.winner(), r.loser(), tiebreakersCsv, rules.scoreBasis(), now));
        }
        results.saveAll(toSave);
        // долговая книга: каждый проигравший должен шаурму каждому победителю.
        // Товарищеский тур — исключение: победитель есть, а шаурмы нет.
        boolean friendly = groupRounds.findByGroupIdAndRoundKey(groupId, week)
                .map(com.example.predictor.domain.GroupRound::isFriendly)
                .orElse(false);
        if (!friendly) {
            debtService.record(groupId, week, toSave, now);
        }
        return true;
    }
}
