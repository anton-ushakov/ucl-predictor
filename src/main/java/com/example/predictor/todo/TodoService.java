package com.example.predictor.todo;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.ShawarmaDebt;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.ShawarmaDebtRepository;
import com.example.predictor.round.RevealPolicy;
import com.example.predictor.round.RoundMatches;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Что сделать сейчас»: одним списком всё, что ждёт участника во всех его группах.
 * Нужен потому, что дела разбросаны по экранам: прогноз здесь, состав тура там, долг в третьем месте.
 */
@Service
public class TodoService {

    /** За сколько до первых игр напоминаем владельцу, что тур не собран. */
    private static final Duration SETUP_WINDOW = Duration.ofDays(7);

    /**
     * kind: PREDICT (есть открытые матчи без вашего прогноза), SELECT_MATCHES (вы владелец,
     * а матчи тура не выбраны), DEBT_OWED (вы должны шаурму), DEBT_DUE (должны вам).
     * deadline: до какого времени это имеет смысл сделать, null — не горит.
     */
    public record TodoItem(String kind, Long groupId, String groupName, LocalDate week,
                           String title, String detail, Instant deadline, int count) {
    }

    public record TodoView(int count, List<TodoItem> items, String note) {
    }

    private final GroupMemberRepository members;
    private final FriendGroupRepository groups;
    private final RoundMatches roundMatches;
    private final RulesService rulesService;
    private final PredictionRepository predictions;
    private final MatchRepository matches;
    private final ShawarmaDebtRepository debts;
    private final AppTime time;

    public TodoService(GroupMemberRepository members, FriendGroupRepository groups,
                       RoundMatches roundMatches, RulesService rulesService,
                       PredictionRepository predictions, MatchRepository matches,
                       ShawarmaDebtRepository debts, AppTime time) {
        this.members = members;
        this.groups = groups;
        this.roundMatches = roundMatches;
        this.rulesService = rulesService;
        this.predictions = predictions;
        this.matches = matches;
        this.debts = debts;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public TodoView forUser(AppUser user) {
        Instant now = Instant.now();
        LocalDate from = time.currentWeek();
        LocalDate to = time.lastPredictableWeek();

        List<TodoItem> items = new ArrayList<>();
        Map<Long, String> groupNames = new HashMap<>();
        for (GroupMember gm : members.findAllOfUser(user.getId())) {
            FriendGroup group = gm.getGroup();
            groupNames.put(group.getId(), group.getName());
            boolean owner = gm.getRole() == GroupRole.OWNER;

            Set<Long> predicted = new HashSet<>(
                    predictions.findMatchIdsOfUser(group.getId(), user.getId(), from, to));

            for (LocalDate week = from; !week.isAfter(to); week = week.plusWeeks(1)) {
                List<FootballMatch> weekMatches = roundMatches.forRound(group.getId(), week);
                if (weekMatches.isEmpty()) {
                    if (owner) {
                        setupItem(group, week, now, items);
                    }
                    continue;
                }
                predictItem(group, week, weekMatches, predicted, now, items);
            }
        }

        debtItems(user, groupNames, items);

        // сначала то, что горит: ближайший срок вверху, дела без срока в конце
        items.sort(Comparator.comparing((TodoItem i) -> i.deadline() == null)
                .thenComparing(i -> i.deadline() == null ? Instant.MAX : i.deadline()));

        String note = items.isEmpty()
                ? "Сейчас ничего не ждёт. Прогнозы сданы, долги закрыты."
                : "Здесь всё, что ждёт вас во всех группах.";
        return new TodoView(items.size(), items, note);
    }

    /** Открытые матчи, на которые участник ещё не ответил. */
    private void predictItem(FriendGroup group, LocalDate week, List<FootballMatch> weekMatches,
                             Set<Long> predicted, Instant now, List<TodoItem> items) {
        Rules rules = rulesService.rulesFor(group, week);
        int open = 0;
        Instant soonest = null;
        for (FootballMatch m : weekMatches) {
            if (m.getStatus() != MatchStatus.SCHEDULED || predicted.contains(m.getId())) {
                continue;
            }
            Instant lockAt = rules.lockAt(m.getKickoffAt());
            if (RevealPolicy.locked(now, lockAt, m.getStatus())) {
                continue;
            }
            open++;
            if (soonest == null || lockAt.isBefore(soonest)) {
                soonest = lockAt;
            }
        }
        if (open == 0) {
            return;
        }
        items.add(new TodoItem("PREDICT", group.getId(), group.getName(), week,
                "Сделать прогноз: " + open + " " + matchWord(open),
                group.getName(), soonest, open));
    }

    /** Владельцу: тур не собран, а игры уже на неделе. */
    private void setupItem(FriendGroup group, LocalDate week, Instant now, List<TodoItem> items) {
        Instant first = matches.firstKickoff(week, MatchStatus.SCHEDULED, now, now.plus(SETUP_WINDOW));
        if (first == null) {
            return;
        }
        items.add(new TodoItem("SELECT_MATCHES", group.getId(), group.getName(), week,
                "Выбрать матчи тура",
                group.getName() + ": без выбранных матчей тура не будет", first, 0));
    }

    private void debtItems(AppUser user, Map<Long, String> groupNames, List<TodoItem> items) {
        Map<Long, Integer> owed = new HashMap<>();
        for (ShawarmaDebt d : debts.findByDebtorIdAndSettledAtIsNull(user.getId())) {
            owed.merge(d.getGroupId(), 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : owed.entrySet()) {
            String name = groupNames.getOrDefault(e.getKey(), "");
            items.add(new TodoItem("DEBT_OWED", e.getKey(), name, null,
                    "Купить шаурму: " + e.getValue() + " " + debtWord(e.getValue()),
                    name, null, e.getValue()));
        }
        Map<Long, Integer> due = new HashMap<>();
        for (ShawarmaDebt d : debts.findByCreditorIdAndSettledAtIsNull(user.getId())) {
            due.merge(d.getGroupId(), 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : due.entrySet()) {
            String name = groupNames.getOrDefault(e.getKey(), "");
            items.add(new TodoItem("DEBT_DUE", e.getKey(), name, null,
                    "Вам должны шаурму: " + e.getValue(),
                    name + ": отметьте, когда отдадут", null, e.getValue()));
        }
    }

    private static String matchWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "матч";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "матча";
        }
        return "матчей";
    }

    private static String debtWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "раз";
        }
        return "раза";
    }
}
