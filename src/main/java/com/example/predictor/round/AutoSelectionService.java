package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupAutoCompetition;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RoundMatch;
import com.example.predictor.group.RulesService;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.GroupAutoCompetitionRepository;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.RoundMatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Автоматический состав тура. Владелец отмечает турнир — например, Лигу чемпионов, — и все его
 * матчи попадают в тур сами. Это страховка от забытого тура: без выбранных матчей тура нет.
 *
 * Добавляются только матчи, приём прогнозов на которые ещё открыт. Убранный вручную матч
 * при включённом автосоставе вернётся — чтобы исключить матч, автосостав турнира выключают.
 */
@Service
public class AutoSelectionService {

    private static final Logger log = LoggerFactory.getLogger(AutoSelectionService.class);

    private final GroupAutoCompetitionRepository autos;
    private final FriendGroupRepository groups;
    private final GroupMemberRepository members;
    private final MatchRepository matches;
    private final RoundMatchRepository selection;
    private final RulesService rulesService;
    private final AppTime time;

    public AutoSelectionService(GroupAutoCompetitionRepository autos, FriendGroupRepository groups,
                                GroupMemberRepository members, MatchRepository matches,
                                RoundMatchRepository selection, RulesService rulesService,
                                AppTime time) {
        this.autos = autos;
        this.groups = groups;
        this.members = members;
        this.matches = matches;
        this.selection = selection;
        this.rulesService = rulesService;
        this.time = time;
    }

    /** Проходит по всем группам с автосоставом. Возвращает число добавленных матчей. */
    @Transactional
    public int run() {
        int added = 0;
        for (GroupAutoCompetition auto : autos.findAll()) {
            try {
                added += apply(auto.getGroupId(), auto.getCompetitionId());
            } catch (RuntimeException e) {
                log.warn("Автосостав группы {} не сработал: {}", auto.getGroupId(), e.getMessage());
            }
        }
        if (added > 0) {
            log.info("Автосостав: матчей добавлено в туры {}", added);
        }
        return added;
    }

    /** Добавляет в туры группы все открытые матчи турнира. */
    @Transactional
    public int apply(Long groupId, Long competitionId) {
        FriendGroup group = groups.findById(groupId).orElse(null);
        if (group == null) {
            return 0;
        }
        Long ownerId = null;
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            if (gm.getRole() == GroupRole.OWNER) {
                ownerId = gm.getUser().getId();
                break;
            }
        }
        if (ownerId == null) {
            return 0;
        }
        LocalDate from = time.currentWeek();
        LocalDate to = time.lastPredictableWeek();
        Instant now = Instant.now();
        List<FootballMatch> list = matches.findForSelection(competitionId, groupId, from, to);
        int added = 0;
        for (FootballMatch m : list) {
            if (m.getStatus() != MatchStatus.SCHEDULED) {
                continue;
            }
            Instant lockAt = rulesService.rulesFor(group, m.getRoundWeek()).lockAt(m.getKickoffAt());
            if (RevealPolicy.locked(now, lockAt, m.getStatus())) {
                continue;
            }
            if (selection.findByGroupIdAndMatchId(groupId, m.getId()).isPresent()) {
                continue;
            }
            selection.save(new RoundMatch(groupId, m.getRoundWeek(), m.getId(), ownerId, now));
            added++;
        }
        return added;
    }
}
