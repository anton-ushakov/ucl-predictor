package com.example.predictor.round;

import com.example.predictor.domain.RoundResult;
import com.example.predictor.repository.RoundResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Жёлтая майка лидера: у кого больше всего выигранных туров в текущем сезоне.
 * При равенстве майка у всех, кто делит первое место, — иначе пришлось бы придумывать,
 * кто из них «главнее», а повода для этого нет.
 */
@Service
public class LeaderService {

    private final RoundResultRepository results;
    private final SeasonService seasons;

    public LeaderService(RoundResultRepository results, SeasonService seasons) {
        this.results = results;
        this.seasons = seasons;
    }

    @Transactional(readOnly = true)
    public Set<Long> leaders(Long groupId) {
        SeasonService.Range range = seasons.rangeOf(groupId, null);
        Map<Long, Integer> wins = new HashMap<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            if (!inRange(r.getRoundWeek(), range)) {
                continue;
            }
            if (r.isWinner()) {
                wins.merge(r.getUserId(), 1, Integer::sum);
            }
        }
        return topOf(wins);
    }

    private static boolean inRange(LocalDate week, SeasonService.Range range) {
        if (range.from() != null && week.isBefore(range.from())) {
            return false;
        }
        return range.to() == null || !week.isAfter(range.to());
    }

    /** Все, у кого побед больше всех. Пустое множество, если побед ни у кого нет. */
    static Set<Long> topOf(Map<Long, Integer> wins) {
        int best = 0;
        for (int value : wins.values()) {
            best = Math.max(best, value);
        }
        Set<Long> leaders = new HashSet<>();
        if (best == 0) {
            return leaders;
        }
        for (Map.Entry<Long, Integer> e : wins.entrySet()) {
            if (e.getValue() == best) {
                leaders.add(e.getKey());
            }
        }
        return leaders;
    }
}
