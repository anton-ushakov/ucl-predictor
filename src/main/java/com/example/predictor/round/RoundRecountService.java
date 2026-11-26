package com.example.predictor.round;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.RoundMatch;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.notify.RecountAlerts;
import com.example.predictor.repository.FriendGroupRepository;
import com.example.predictor.repository.RoundMatchRepository;
import com.example.predictor.repository.RoundResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Пересчёт итогов туров, когда счёт матча оказался другим.
 *
 * Источник отдаёт матч сыгранным по ходу своего протокола, и счёт в нём иногда меняется уже
 * после финального свистка: отменили гол, засчитали автогол другому. Если по такому матчу
 * итоги уже подведены, в таблице остаются чужие баллы, а у кого-то и чужая шаурма. Поэтому
 * поправленный счёт запускает пересчёт сам, а владелец может позвать его руками.
 */
@Service
public class RoundRecountService {

    private static final Logger log = LoggerFactory.getLogger(RoundRecountService.class);

    /** Тур, итоги которого пересчитали. */
    public record Round(Long groupId, LocalDate roundKey) {
    }

    private final RoundMatchRepository selection;
    private final RoundResultRepository results;
    private final FriendGroupRepository groups;
    private final RoundFinalizer finalizer;
    private final RecountAlerts alerts;
    private final GroupAccess access;
    private final AdminAccess admins;

    public RoundRecountService(RoundMatchRepository selection, RoundResultRepository results,
                               FriendGroupRepository groups, RoundFinalizer finalizer,
                               RecountAlerts alerts, GroupAccess access, AdminAccess admins) {
        this.selection = selection;
        this.results = results;
        this.groups = groups;
        this.finalizer = finalizer;
        this.alerts = alerts;
        this.access = access;
        this.admins = admins;
    }

    /**
     * Пересчитывает итоги всех подведённых туров, в которые входят эти матчи. Туры, итоги
     * которых ещё не подводились, трогать не нужно: их посчитают обычным порядком.
     */
    public int recountRoundsOf(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return 0;
        }
        Set<Round> rounds = new LinkedHashSet<>();
        for (RoundMatch rm : selection.findByMatchIdIn(matchIds)) {
            rounds.add(new Round(rm.getGroupId(), rm.getRoundWeek()));
        }
        int done = 0;
        for (Round round : rounds) {
            if (!results.existsByGroupIdAndRoundWeek(round.groupId(), round.roundKey())) {
                continue;
            }
            try {
                if (recount(round, "Источник уточнил счёт матча.")) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("Не удалось пересчитать итоги тура {} группы {}: {}",
                        round.roundKey(), round.groupId(), e.getMessage());
            }
        }
        return done;
    }

    /** Пересчёт по кнопке: владелец группы или владелец приложения. */
    public String recountByHand(AppUser user, Long groupId, LocalDate roundKey) {
        Membership ms = access.require(user, groupId);
        if (!ms.isOwner() && !admins.isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Пересчитать итоги может владелец группы");
        }
        if (!results.existsByGroupIdAndRoundWeek(groupId, roundKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Итоги этого тура ещё не подведены — пересчитывать нечего");
        }
        boolean done = recount(new Round(groupId, roundKey),
                "Итоги пересчитали по текущему счёту матчей.");
        return done
                ? "Итоги пересчитаны по текущему счёту. Участникам отправлено оповещение"
                : "Пересчитать не удалось: по какому-то матчу тура счёт ещё не окончательный";
    }

    private boolean recount(Round round, String reason) {
        FriendGroup group = groups.findById(round.groupId()).orElse(null);
        if (group == null) {
            return false;
        }
        if (!finalizer.recount(round.groupId(), round.roundKey())) {
            return false;
        }
        alerts.recounted(group, round.roundKey(), reason, Instant.now());
        return true;
    }
}
