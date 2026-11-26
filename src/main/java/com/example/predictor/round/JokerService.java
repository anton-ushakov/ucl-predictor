package com.example.predictor.round;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.RoundJoker;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.group.RulesService;
import com.example.predictor.repository.RoundJokerRepository;
import com.example.predictor.group.RulesService.Rules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Джокер: один матч тура, за который баллы считаются вдвойне. Выбирается, пока приём прогнозов
 * на этот матч открыт, и переносится на другой матч столько раз, сколько захочется — но только
 * до закрытия. После закрытия выбор уже сделан и виден всем, иначе смысл риска теряется.
 */
@Service
public class JokerService {

    /** matchId=null: джокер снят. */
    public record JokerResult(Long matchId, String message) {
    }

    private final GroupAccess access;
    private final RulesService rulesService;
    private final RoundMatches roundMatches;
    private final RoundJokerRepository jokers;
    private final AppTime time;

    public JokerService(GroupAccess access, RulesService rulesService, RoundMatches roundMatches,
                        RoundJokerRepository jokers, AppTime time) {
        this.access = access;
        this.rulesService = rulesService;
        this.roundMatches = roundMatches;
        this.jokers = jokers;
        this.time = time;
    }

    /** Джокеры всех участников тура: userId → matchId. */
    @Transactional(readOnly = true)
    public Map<Long, Long> forRound(Long groupId, LocalDate week) {
        Map<Long, Long> result = new HashMap<>();
        for (RoundJoker j : jokers.findByGroupIdAndRoundWeek(groupId, week)) {
            result.put(j.getUserId(), j.getMatchId());
        }
        return result;
    }

    @Transactional
    public JokerResult set(AppUser user, Long groupId, LocalDate week, Long matchId) {
        Membership ms = access.require(user, groupId);
        FriendGroup group = ms.group();
        RoundJoker existing = jokers
                .findByGroupIdAndRoundWeekAndUserId(groupId, week, user.getId())
                .orElse(null);

        if (matchId == null) {
            if (existing != null) {
                requireOpen(group, week, existing.getMatchId(), "Этот матч уже закрыт: джокер остаётся на нём");
                jokers.delete(existing);
            }
            return new JokerResult(null, "Джокер снят");
        }

        if (!group.isJokerEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Джокер в этой группе выключен: включите его в настройках");
        }
        if (week.isAfter(time.lastRoundKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Этот тур ещё не открыт");
        }
        if (existing != null && !existing.getMatchId().equals(matchId)) {
            requireOpen(group, week, existing.getMatchId(),
                    "Матч с джокером уже закрыт: перенести его нельзя");
        }
        FootballMatch match = requireOpen(group, week, matchId,
                "Приём прогнозов на этот матч закрыт: джокер на него уже не поставить");

        Instant now = Instant.now();
        if (existing == null) {
            jokers.save(new RoundJoker(groupId, week, user.getId(), match.getId(), now));
        } else {
            existing.moveTo(match.getId(), now);
            jokers.save(existing);
        }
        return new JokerResult(match.getId(), "Джокер на этом матче: баллы за него считаются вдвойне");
    }

    /** Матч должен быть в туре, и приём прогнозов на него должен быть ещё открыт. */
    private FootballMatch requireOpen(FriendGroup group, LocalDate week, Long matchId, String closedMessage) {
        List<FootballMatch> weekMatches = roundMatches.forRound(group.getId(), week);
        FootballMatch match = null;
        for (FootballMatch m : weekMatches) {
            if (m.getId().equals(matchId)) {
                match = m;
                break;
            }
        }
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Этого матча нет в туре");
        }
        Rules rules = rulesService.rulesFor(group, week);
        boolean locked = RevealPolicy.locked(Instant.now(), rules.lockAt(match.getKickoffAt()),
                match.getStatus());
        if (locked || match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, closedMessage);
        }
        return match;
    }
}
