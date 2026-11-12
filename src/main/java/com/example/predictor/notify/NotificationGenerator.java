package com.example.predictor.notify;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.Notification;
import com.example.predictor.domain.NotificationSettings;
import com.example.predictor.domain.NotificationType;
import com.example.predictor.domain.Prediction;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.group.RulesService;
import com.example.predictor.group.RulesService.Rules;
import com.example.predictor.notify.NotificationTexts.Text;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.NotificationRepository;
import com.example.predictor.repository.NotificationSettingsRepository;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.scoring.Scoring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Готовит оповещения: час до закрытия приёма, итог каждого матча, итоги дня и тура.
 * Запускается вместе с остальной фоновой работой. Одно и то же оповещение не присылается дважды:
 * за этим следит ключ dedup_key.
 *
 * <p>Чтобы при первом запуске не прислать гору старых оповещений, берём только свежие события:
 * матчи, начавшиеся за последние сутки, и туры, итоги которых подведены за последние сутки.
 */
@Service
public class NotificationGenerator {

    private static final Logger log = LoggerFactory.getLogger(NotificationGenerator.class);

    /** За сколько до закрытия приёма предупреждаем. */
    static final Duration LOCK_WARNING = Duration.ofHours(1);

    /** Насколько старые события ещё интересны. */
    private static final Duration FRESH = Duration.ofHours(24);


    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final com.example.predictor.round.RoundMatches roundMatches;
    private final PredictionRepository predictions;
    private final RoundResultRepository results;
    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final RulesService rulesService;
    private final AppTime time;

    public NotificationGenerator(FriendGroupRepository groups, GroupMemberRepository members,
                                 com.example.predictor.round.RoundMatches roundMatches,
                                 PredictionRepository predictions,
                                 RoundResultRepository results, NotificationRepository notifications,
                                 NotificationSettingsRepository settings, RulesService rulesService,
                                 AppTime time) {
        this.groups = groups;
        this.members = members;
        this.roundMatches = roundMatches;
        this.predictions = predictions;
        this.results = results;
        this.notifications = notifications;
        this.settings = settings;
        this.rulesService = rulesService;
        this.time = time;
    }

    /** Готовит все оповещения. Возвращает, сколько создано. */
    @Transactional
    public int generate() {
        Instant now = Instant.now();
        List<FriendGroup> allGroups = groups.findAll();
        if (allGroups.isEmpty()) {
            return 0;
        }
        List<Notification> created = new ArrayList<>();
        for (FriendGroup group : allGroups) {
            List<GroupMember> memberList = members.findAllWithUser(group.getId());
            if (memberList.isEmpty()) {
                continue;
            }
            Map<Long, NotificationSettings> wants = settingsOf(memberList);
            try {
                lockSoon(group, memberList, wants, now, created);
                matchResults(group, memberList, wants, now, created);
                daySummaries(group, memberList, wants, now, created);
                roundSummaries(group, memberList, wants, now, created);
            } catch (RuntimeException e) {
                log.warn("Не удалось подготовить оповещения для группы {}: {}",
                        group.getId(), e.getMessage());
            }
        }
        List<Notification> fresh = withoutDuplicates(created);
        if (!fresh.isEmpty()) {
            notifications.saveAll(fresh);
            log.info("Подготовлено оповещений: {}", fresh.size());
        }
        return fresh.size();
    }

    // ------------------------------------------------------------------ час до закрытия

    private void lockSoon(FriendGroup group, List<GroupMember> memberList,
                          Map<Long, NotificationSettings> wants, Instant now, List<Notification> out) {
        LocalDate from = time.currentWeek();
        LocalDate to = time.lastPredictableWeek();
        Instant until = now.plus(LOCK_WARNING);

        // матчи, приём на которые закроется в ближайший час
        List<FootballMatch> soon = new ArrayList<>();
        Instant earliestLock = null;
        for (FootballMatch m : roundMatches.forRangeFlat(group.getId(), from, to)) {
            if (m.getStatus() != MatchStatus.SCHEDULED) {
                continue;
            }
            Instant lockAt = rulesService.rulesFor(group, m.getRoundWeek()).lockAt(m.getKickoffAt());
            if (lockAt.isAfter(now) && !lockAt.isAfter(until)) {
                soon.add(m);
                if (earliestLock == null || lockAt.isBefore(earliestLock)) {
                    earliestLock = lockAt;
                }
            }
        }
        if (soon.isEmpty()) {
            return;
        }

        Set<LocalDate> weeks = new HashSet<>();
        soon.forEach(m -> weeks.add(m.getRoundWeek()));
        // кто на какие матчи уже сохранил прогноз
        Map<Long, Set<Long>> predictedBy = new HashMap<>();
        for (LocalDate week : weeks) {
            for (Prediction p : predictions.findForRound(group.getId(), week)) {
                predictedBy.computeIfAbsent(p.getUser().getId(), k -> new HashSet<>())
                        .add(p.getMatch().getId());
            }
        }

        String key = "LOCK:" + group.getId() + ":" + earliestLock.getEpochSecond();
        for (GroupMember gm : memberList) {
            Long userId = gm.getUser().getId();
            if (!wanted(wants, userId, NotificationType.LOCK_SOON)) {
                continue;
            }
            Set<Long> mine = predictedBy.getOrDefault(userId, Set.of());
            int left = (int) soon.stream().filter(m -> !mine.contains(m.getId())).count();
            if (left == 0) {
                continue; // всё уже сохранено, тревожить незачем
            }
            Text text = NotificationTexts.lockSoon(group.getName(), left);
            out.add(new Notification(userId, group.getId(), NotificationType.LOCK_SOON, key,
                    text.title(), text.body(), soon.get(0).getRoundWeek(), now));
        }
    }

    // ------------------------------------------------------------------ итог матча

    private void matchResults(FriendGroup group, List<GroupMember> memberList,
                              Map<Long, NotificationSettings> wants, Instant now,
                              List<Notification> out) {
        LocalDate from = time.currentWeek().minusWeeks(1);
        LocalDate to = time.currentWeek();
        Instant freshSince = now.minus(FRESH);

        Map<LocalDate, List<Prediction>> byWeek = new HashMap<>();
        for (FootballMatch m : roundMatches.forRangeFlat(group.getId(), from, to)) {
            if (!m.isFinished() || m.getKickoffAt().isBefore(freshSince)) {
                continue;
            }
            Rules rules = rulesService.rulesFor(group, m.getRoundWeek());
            ScoreBasis basis = rules.scoreBasis();
            if (!m.missingParts(basis).isEmpty()) {
                continue; // ждём решения группы, какой счёт считать
            }
            List<Prediction> roundPredictions = byWeek.computeIfAbsent(m.getRoundWeek(),
                    week -> predictions.findForRound(group.getId(), week));

            Map<Long, Prediction> mine = new HashMap<>();
            for (Prediction p : roundPredictions) {
                if (p.getMatch().getId().equals(m.getId())) {
                    mine.put(p.getUser().getId(), p);
                }
            }
            int homeScore = m.scoreHome(basis);
            int awayScore = m.scoreAway(basis);
            String key = "MATCH:" + group.getId() + ":" + m.getId();

            for (GroupMember gm : memberList) {
                Long userId = gm.getUser().getId();
                if (!wanted(wants, userId, NotificationType.MATCH_FINISHED)) {
                    continue;
                }
                Prediction p = mine.get(userId);
                String guess = p == null ? null : p.getHomeGoals() + ":" + p.getAwayGoals();
                Integer points = p == null ? null
                        : rules.scoring().points(p.getHomeGoals(), p.getAwayGoals(),
                                homeScore, awayScore);
                Text text = NotificationTexts.matchFinished(group.getName(), m.getHomeTeam(),
                        m.getAwayTeam(), homeScore, awayScore, guess, points);
                out.add(new Notification(userId, group.getId(), NotificationType.MATCH_FINISHED, key,
                        text.title(), text.body(), m.getRoundWeek(), now));
            }
        }
    }

    // ------------------------------------------------------------------ итоги дня

    private void daySummaries(FriendGroup group, List<GroupMember> memberList,
                              Map<Long, NotificationSettings> wants, Instant now,
                              List<Notification> out) {
        LocalDate from = time.currentWeek().minusWeeks(1);
        LocalDate to = time.currentWeek();
        Instant freshSince = now.minus(FRESH);

        // матчи по игровым дням (по времени группы)
        Map<LocalDate, List<FootballMatch>> byDay = new LinkedHashMap<>();
        for (FootballMatch m : roundMatches.forRangeFlat(group.getId(), from, to)) {
            byDay.computeIfAbsent(m.getKickoffAt().atZone(time.zone()).toLocalDate(),
                    d -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<LocalDate, List<FootballMatch>> e : byDay.entrySet()) {
            List<FootballMatch> dayMatches = e.getValue();
            boolean over = dayMatches.stream().allMatch(m -> m.getStatus().isTerminal());
            boolean fresh = dayMatches.stream().anyMatch(m -> !m.getKickoffAt().isBefore(freshSince));
            if (!over || !fresh) {
                continue;
            }
            LocalDate week = dayMatches.get(0).getRoundWeek();
            Rules dayRules = rulesService.rulesFor(group, week);
            ScoreBasis basis = dayRules.scoreBasis();
            Scoring scoring = dayRules.scoring();
            List<FootballMatch> counted = dayMatches.stream()
                    .filter(m -> m.isFinished() && m.missingParts(basis).isEmpty())
                    .toList();
            if (counted.isEmpty()) {
                continue;
            }

            // баллы за день по каждому участнику
            Map<Long, Integer> dayPoints = new HashMap<>();
            Map<Long, Integer> weekPoints = new HashMap<>();
            for (Prediction p : predictions.findForRound(group.getId(), week)) {
                FootballMatch m = p.getMatch();
                if (!m.isFinished() || !m.missingParts(basis).isEmpty()) {
                    continue;
                }
                int points = scoring.points(p.getHomeGoals(), p.getAwayGoals(),
                        m.scoreHome(basis), m.scoreAway(basis));
                weekPoints.merge(p.getUser().getId(), points, Integer::sum);
                if (counted.stream().anyMatch(c -> c.getId().equals(m.getId()))) {
                    dayPoints.merge(p.getUser().getId(), points, Integer::sum);
                }
            }
            if (weekPoints.isEmpty()) {
                continue; // прогнозов не было: подводить нечего
            }

            int best = weekPoints.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            List<String> leaders = new ArrayList<>();
            for (GroupMember gm : memberList) {
                if (weekPoints.getOrDefault(gm.getUser().getId(), -1) == best) {
                    leaders.add(gm.getUser().getUsername());
                }
            }

            String key = "DAY:" + group.getId() + ":" + e.getKey();
            for (GroupMember gm : memberList) {
                Long userId = gm.getUser().getId();
                if (!wanted(wants, userId, NotificationType.SUMMARY)
                        || !weekPoints.containsKey(userId)) {
                    continue; // тем, кто не участвует в туре, итоги дня не шлём
                }
                Text text = NotificationTexts.daySummary(group.getName(), e.getKey(),
                        dayPoints.getOrDefault(userId, 0), leaders, best);
                out.add(new Notification(userId, group.getId(), NotificationType.SUMMARY, key,
                        text.title(), text.body(), week, now));
            }
        }
    }

    // ------------------------------------------------------------------ итоги тура

    private void roundSummaries(FriendGroup group, List<GroupMember> memberList,
                                Map<Long, NotificationSettings> wants, Instant now,
                                List<Notification> out) {
        Instant freshSince = now.minus(FRESH);
        Map<LocalDate, List<RoundResult>> byWeek = new LinkedHashMap<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(group.getId())) {
            if (r.getFinalizedAt().isBefore(freshSince)) {
                continue;
            }
            byWeek.computeIfAbsent(r.getRoundWeek(), w -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<LocalDate, List<RoundResult>> e : byWeek.entrySet()) {
            List<String> winners = new ArrayList<>();
            List<String> losers = new ArrayList<>();
            Map<Long, RoundResult> byUser = new HashMap<>();
            Map<Long, String> names = new HashMap<>();
            memberList.forEach(gm -> names.put(gm.getUser().getId(), gm.getUser().getUsername()));
            for (RoundResult r : e.getValue()) {
                byUser.put(r.getUserId(), r);
                String name = names.getOrDefault(r.getUserId(), "?");
                if (r.isWinner()) {
                    winners.add(name);
                }
                if (r.isLoser()) {
                    losers.add(name);
                }
            }

            String key = "ROUND:" + group.getId() + ":" + e.getKey();
            for (GroupMember gm : memberList) {
                Long userId = gm.getUser().getId();
                if (!wanted(wants, userId, NotificationType.SUMMARY)) {
                    continue;
                }
                RoundResult mine = byUser.get(userId);
                Text text = mine == null
                        ? NotificationTexts.roundSummaryForNonParticipant(group.getName(), winners, losers)
                        : NotificationTexts.roundSummary(group.getName(), mine.getPlace(),
                                mine.getPoints(), winners, losers);
                out.add(new Notification(userId, group.getId(), NotificationType.SUMMARY, key,
                        text.title(), text.body(), e.getKey(), now));
            }
        }
    }

    // ------------------------------------------------------------------ вспомогательное

    private Map<Long, NotificationSettings> settingsOf(List<GroupMember> memberList) {
        List<Long> ids = memberList.stream().map(gm -> gm.getUser().getId()).toList();
        Map<Long, NotificationSettings> map = new HashMap<>();
        for (NotificationSettings s : settings.findByUserIdIn(ids)) {
            map.put(s.getUserId(), s);
        }
        return map;
    }

    /** Если участник ничего не настраивал, присылаем все виды оповещений. */
    private static boolean wanted(Map<Long, NotificationSettings> wants, Long userId,
                                  NotificationType type) {
        NotificationSettings s = wants.get(userId);
        return s == null || s.wants(type);
    }

    /** Убирает те оповещения, которые уже были отправлены раньше. */
    private List<Notification> withoutDuplicates(List<Notification> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<Long> userIds = new HashSet<>();
        Set<String> keys = new HashSet<>();
        candidates.forEach(n -> {
            userIds.add(n.getUserId());
            keys.add(n.getDedupKey());
        });
        Set<String> existing = new HashSet<>();
        for (Notification n : notifications.findExisting(userIds, keys)) {
            existing.add(n.getUserId() + "|" + n.getDedupKey());
        }
        Set<String> seen = new HashSet<>();
        List<Notification> fresh = new ArrayList<>();
        for (Notification n : candidates) {
            String unique = n.getUserId() + "|" + n.getDedupKey();
            if (existing.contains(unique) || !seen.add(unique)) {
                continue;
            }
            fresh.add(n);
        }
        return fresh;
    }
}
