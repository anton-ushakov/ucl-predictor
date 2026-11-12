package com.example.predictor.round;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Кто участвует в туре. Участвует только тот, кто сохранил хотя бы один прогноз на этот тур.
 * Кто не сделал ни одного прогноза, в туре не участвует: он не попадает в таблицу, не может стать
 * проигравшим и ничего не должен. Если участник сделал прогнозы лишь на часть матчей, то за остальные
 * ему начисляется 0 баллов.
 */
public final class RoundParticipants {

    public record Member(long userId, Instant joinedAt) {
    }

    private RoundParticipants() {
    }

    public static List<Long> select(List<Member> members, Set<Long> predictorIds) {
        return members.stream()
                .filter(m -> predictorIds.contains(m.userId()))
                .sorted(Comparator.comparing(Member::joinedAt).thenComparingLong(Member::userId))
                .map(Member::userId)
                .toList();
    }
}
