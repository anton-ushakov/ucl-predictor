package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.GroupRound;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RoundMatch;
import com.example.predictor.domain.StandingRowEntity;
import com.example.predictor.group.RulesService;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.RoundMatchRepository;
import com.example.predictor.repository.StandingRowRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Топ-матчи»: подборка самых интересных матчей пяти больших чемпионатов.
 *
 * Правило простое и честное: берём матч, если сумма мест его команд в своей таблице не
 * больше десяти. Второй против восьмого — десять, идёт; третий против шестого — девять,
 * идёт; десятый против двенадцатого — нет. Так в тур попадают дерби лидеров и не попадают
 * игры из середины таблицы, и список не зависит от чьего-то вкуса.
 *
 * Ориентир — номер тура чемпионата, а не календарь: тур Англии бывает растянут с пятницы на
 * вторник, и делить его по неделям бессмысленно. Матчи разных стран с одним и тем же номером
 * тура попадают в один тур группы: на пять чемпионатов это обычно три-шесть матчей — как раз
 * на один вечер прогнозов.
 */
@Service
public class TopMatchesService {

    private static final Logger log = LoggerFactory.getLogger(TopMatchesService.class);

    /** Коды чемпионатов, из которых собираем подборку. */
    static final List<String> LEAGUES = List.of("PL", "PD", "SA", "FL1", "BL1");

    /** Сумма мест команд, при которой матч считается топовым. */
    static final int MAX_PLACES_SUM = 10;

    /** Название тура с подборкой. */
    static final String TITLE = "Топ-матчи";

    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final CompetitionRepository competitions;
    private final MatchRepository matches;
    private final StandingRowRepository standings;
    private final RoundMatchRepository selection;
    private final AutoRounds autoRounds;
    private final RulesService rulesService;
    private final AppTime time;

    public TopMatchesService(FriendGroupRepository groups, GroupMemberRepository members,
                             CompetitionRepository competitions, MatchRepository matches,
                             StandingRowRepository standings, RoundMatchRepository selection,
                             AutoRounds autoRounds, RulesService rulesService, AppTime time) {
        this.groups = groups;
        this.members = members;
        this.competitions = competitions;
        this.matches = matches;
        this.standings = standings;
        this.selection = selection;
        this.autoRounds = autoRounds;
        this.rulesService = rulesService;
        this.time = time;
    }

    /** Матч подборки: нужен и для показа, и для отбора. */
    public record Pick(FootballMatch match, int placesSum, Integer matchday) {
    }

    /** Проходит по группам, у которых подборка включена. Возвращает число добавленных матчей. */
    @Transactional
    public int run() {
        int added = 0;
        for (FriendGroup group : groups.findAll()) {
            if (!group.isTopMatches()) {
                continue;
            }
            try {
                added += apply(group);
            } catch (RuntimeException e) {
                log.warn("Топ-матчи группы {} не собрались: {}", group.getId(), e.getMessage());
            }
        }
        if (added > 0) {
            log.info("Топ-матчи: матчей добавлено в туры {}", added);
        }
        return added;
    }

    /** Собирает подборку для одной группы. */
    @Transactional
    public int apply(FriendGroup group) {
        Long ownerId = ownerOf(group.getId());
        if (ownerId == null) {
            return 0;
        }
        Instant now = Instant.now();
        int added = 0;
        for (Pick pick : picks()) {
            FootballMatch m = pick.match();
            if (selection.findByGroupIdAndMatchId(group.getId(), m.getId()).isPresent()) {
                continue;
            }
            // туры разных стран с одним номером идут в один тур: «Топ-матчи» к 14-му туру
            String sourceKey = "TOP:" + (pick.matchday() == null
                    ? "d" + LocalDate.ofInstant(m.getKickoffAt(), time.zone())
                    : String.valueOf(pick.matchday()));
            GroupRound round = autoRounds.findOrCreate(group.getId(), sourceKey, TITLE,
                    LocalDate.ofInstant(m.getKickoffAt(), time.zone()), ownerId, now);
            Instant lockAt = rulesService.rulesFor(group, round.getRoundKey())
                    .lockAt(m.getKickoffAt());
            if (RevealPolicy.locked(now, lockAt, m.getStatus())) {
                continue;
            }
            selection.save(new RoundMatch(group.getId(), round.getRoundKey(), m.getId(), ownerId,
                    now));
            added++;
        }
        return added;
    }

    /**
     * Матчи, прошедшие правило суммы мест, из всех пяти чемпионатов. Считается один раз на
     * прогон и годится для всех групп: правило от группы не зависит.
     */
    @Transactional(readOnly = true)
    public List<Pick> picks() {
        LocalDate from = time.currentWeek();
        LocalDate to = time.lastPredictableWeek().plusDays(6);
        List<Pick> picks = new ArrayList<>();
        for (Competition competition : competitions.findByEnabledTrueOrderBySortOrderAscNameAsc()) {
            if (competition.getCode() == null || !LEAGUES.contains(competition.getCode())) {
                continue;
            }
            Map<Long, Integer> places = placesOf(competition.getId());
            if (places.isEmpty()) {
                // таблицы ещё нет — без неё правило не посчитать
                continue;
            }
            for (FootballMatch m : matches.findForSelection(competition.getId(), null, from, to)) {
                if (m.getStatus() != MatchStatus.SCHEDULED) {
                    continue;
                }
                Integer home = places.get(m.getHomeTeamId());
                Integer away = places.get(m.getAwayTeamId());
                if (home == null || away == null) {
                    continue;
                }
                int sum = home + away;
                if (sum <= MAX_PLACES_SUM) {
                    picks.add(new Pick(m, sum, m.getMatchday()));
                }
            }
        }
        // сначала самые заметные пары: второй с третьим впереди второго с восьмым
        picks.sort(Comparator.comparingInt(Pick::placesSum)
                .thenComparing(p -> p.match().getKickoffAt()));
        return picks;
    }

    /** Место каждой команды в таблице своего чемпионата: номер команды у источника → место. */
    private Map<Long, Integer> placesOf(Long competitionId) {
        Map<Long, Integer> places = new HashMap<>();
        for (StandingRowEntity row
                : standings.findByCompetitionIdOrderByGroupLabelAscPositionAsc(competitionId)) {
            if (row.getTeamExternalId() != null) {
                places.put(row.getTeamExternalId(), row.getPosition());
            }
        }
        return places;
    }

    private Long ownerOf(Long groupId) {
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            if (gm.getRole() == GroupRole.OWNER) {
                return gm.getUser().getId();
            }
        }
        return null;
    }
}
