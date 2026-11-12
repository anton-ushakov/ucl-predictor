package com.example.predictor.round;

import com.example.predictor.domain.FootballMatch;
import com.example.predictor.domain.RoundMatch;
import com.example.predictor.repository.MatchRepository;
import com.example.predictor.repository.RoundMatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Матчи тура — это те, которые владелец группы отобрал сам. Раньше в тур попадали все матчи
 * недели, теперь состав тура у каждой группы свой, и весь остальной код спрашивает его здесь.
 */
@Service
public class RoundMatches {

    private static final Comparator<FootballMatch> ORDER =
            Comparator.comparing(FootballMatch::getKickoffAt).thenComparing(FootballMatch::getId);

    private final RoundMatchRepository selection;
    private final MatchRepository matches;

    public RoundMatches(RoundMatchRepository selection, MatchRepository matches) {
        this.selection = selection;
        this.matches = matches;
    }

    /** Матчи тура группы в порядке начала. Пусто, если владелец ещё ничего не отобрал. */
    public List<FootballMatch> forRound(Long groupId, LocalDate week) {
        List<Long> ids = new ArrayList<>();
        for (RoundMatch rm : selection.findByGroupIdAndRoundWeek(groupId, week)) {
            ids.add(rm.getMatchId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<FootballMatch> list = new ArrayList<>(matches.findAllById(ids));
        list.sort(ORDER);
        return list;
    }

    /** Матчи туров за промежуток недель, сгруппированные по неделе (недели по возрастанию). */
    public Map<LocalDate, List<FootballMatch>> forRange(Long groupId, LocalDate from, LocalDate to) {
        List<RoundMatch> rows = selection.findByGroupIdAndRoundWeekBetween(groupId, from, to);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDate> weekOfMatch = new LinkedHashMap<>();
        for (RoundMatch rm : rows) {
            weekOfMatch.put(rm.getMatchId(), rm.getRoundWeek());
        }
        List<FootballMatch> all = new ArrayList<>(matches.findAllById(weekOfMatch.keySet()));
        all.sort(ORDER);

        Map<LocalDate, List<FootballMatch>> byWeek = new java.util.TreeMap<>();
        for (FootballMatch m : all) {
            byWeek.computeIfAbsent(weekOfMatch.get(m.getId()), k -> new ArrayList<>()).add(m);
        }
        return byWeek;
    }

    /** То же, что forRange, но одним списком в порядке начала матчей. */
    public List<FootballMatch> forRangeFlat(Long groupId, LocalDate from, LocalDate to) {
        List<FootballMatch> all = new ArrayList<>();
        for (List<FootballMatch> week : forRange(groupId, from, to).values()) {
            all.addAll(week);
        }
        all.sort(ORDER);
        return all;
    }

    /** Недели, в которых у группы есть отобранные матчи, от новых к старым. */
    public List<LocalDate> weeks(Long groupId) {
        return selection.findWeeks(groupId);
    }
}
