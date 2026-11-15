package com.example.predictor.round;

import com.example.predictor.common.UserLabel;
import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.ShawarmaDebt;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.group.GroupAccess.Membership;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.ShawarmaDebtRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Долговая книга: кто кому должен шаурму и отдан ли долг. Запись появляется при подведении
 * итогов тура, отметку «отдал» ставит тот, кому должны, или владелец группы — не сам должник.
 */
@Service
public class DebtService {

    /** canSettle=true: смотрящий может отметить долг отданным или вернуть отметку. */
    public record DebtDto(Long id, LocalDate week, Long debtorId, String debtor, String debtorBadge,
                          Long creditorId, String creditor, String creditorBadge,
                          boolean settled, Instant settledAt, boolean canSettle) {
    }

    public record DebtsView(int open, int settled, List<DebtDto> debts) {
    }

    private final GroupAccess access;
    private final ShawarmaDebtRepository debts;
    private final GroupMemberRepository members;

    public DebtService(GroupAccess access, ShawarmaDebtRepository debts,
                       GroupMemberRepository members) {
        this.access = access;
        this.debts = debts;
        this.members = members;
    }

    /** Записывает долги по итогам тура: каждый проигравший должен каждому победителю. */
    @Transactional
    public int record(Long groupId, LocalDate week, List<RoundResult> rows, Instant now) {
        int created = 0;
        for (RoundResult loser : rows) {
            if (!loser.isLoser()) {
                continue;
            }
            for (RoundResult winner : rows) {
                if (!winner.isWinner() || winner.getUserId().equals(loser.getUserId())) {
                    continue;
                }
                if (debts.existsByGroupIdAndRoundWeekAndDebtorIdAndCreditorId(
                        groupId, week, loser.getUserId(), winner.getUserId())) {
                    continue;
                }
                debts.save(new ShawarmaDebt(groupId, week, loser.getUserId(), winner.getUserId(), now));
                created++;
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public DebtsView view(AppUser user, Long groupId) {
        Membership ms = access.require(user, groupId);
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        List<DebtDto> open = new ArrayList<>();
        List<DebtDto> done = new ArrayList<>();
        for (ShawarmaDebt d : debts.findByGroupIdOrderByRoundWeekDescIdAsc(groupId)) {
            DebtDto dto = toDto(d, user, ms.isOwner(), names);
            (d.isSettled() ? done : open).add(dto);
        }
        // сначала то, что ещё не отдано
        List<DebtDto> all = new ArrayList<>(open);
        all.addAll(done);
        return new DebtsView(open.size(), done.size(), all);
    }

    @Transactional
    public DebtDto settle(AppUser user, Long groupId, Long debtId, boolean settled) {
        Membership ms = access.require(user, groupId);
        ShawarmaDebt debt = debts.findById(debtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Долг не найден"));
        if (!debt.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Долг не найден");
        }
        if (!debt.getCreditorId().equals(user.getId()) && !ms.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Отметить долг отданным может тот, кому должны, или владелец группы");
        }
        if (settled) {
            debt.settle(user.getId(), Instant.now());
        } else {
            debt.reopen();
        }
        debts.save(debt);
        Map<Long, UserLabel> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), UserLabel.of(gm.getUser()));
        }
        return toDto(debt, user, ms.isOwner(), names);
    }

    private static DebtDto toDto(ShawarmaDebt d, AppUser me, boolean iAmOwner,
                                 Map<Long, UserLabel> names) {
        UserLabel debtor = names.getOrDefault(d.getDebtorId(), UserLabel.UNKNOWN);
        UserLabel creditor = names.getOrDefault(d.getCreditorId(), UserLabel.UNKNOWN);
        return new DebtDto(d.getId(), d.getRoundWeek(), d.getDebtorId(), debtor.username(),
                debtor.badge(), d.getCreditorId(), creditor.username(), creditor.badge(),
                d.isSettled(), d.getSettledAt(),
                iAmOwner || d.getCreditorId().equals(me.getId()));
    }
}
