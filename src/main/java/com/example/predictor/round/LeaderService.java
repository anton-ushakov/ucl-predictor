package com.example.predictor.round;

import com.example.predictor.domain.RoundResult;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.standings.LeaderboardCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Майки у имени участника.
 *
 * Жёлтая — лидер сезона: тот, кто идёт первым в общем зачёте. Именно первым, один: майка
 * лидера на двоих — это уже не лидер, и в таблице она перестаёт что-либо значить. Порядок
 * берём тот же, которым считается общий зачёт, поэтому майка всегда у верхней строки —
 * спорить с таблицей она не может.
 *
 * Красная — победитель последнего сыгранного тура. Это отдельная история: лидер сезона
 * набегает месяцами, а красная майка меняется каждую неделю и показывает, кто в форме
 * сейчас. Если тур выиграли двое, красная у обоих: там место и правда общее. У одного
 * человека могут быть обе майки сразу.
 */
@Service
public class LeaderService {

    /** Кому какие майки положены. */
    public record Jerseys(Long seasonLeader, Set<Long> lastRoundWinners) {

        public static final Jerseys NONE = new Jerseys(null, Set.of());

        /** Жёлтая: лидер сезона. */
        public boolean leader(Long userId) {
            return seasonLeader != null && seasonLeader.equals(userId);
        }

        /** Красная: победитель последнего тура. */
        public boolean lastWinner(Long userId) {
            return lastRoundWinners.contains(userId);
        }
    }

    private final RoundResultRepository results;
    private final SeasonService seasons;
    private final com.example.predictor.questions.QuestionService questions;

    public LeaderService(RoundResultRepository results, SeasonService seasons,
                         com.example.predictor.questions.QuestionService questions) {
        this.results = results;
        this.seasons = seasons;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public Jerseys jerseys(Long groupId) {
        SeasonService.Range range = seasons.rangeOf(groupId, null);
        List<RoundResult> rows = new ArrayList<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            if (inRange(r.getRoundWeek(), range)) {
                rows.add(r);
            }
        }
        if (rows.isEmpty()) {
            return Jerseys.NONE;
        }
        return new Jerseys(
                leaderOf(rows, groupId, range),
                winnersOfLastRound(rows));
    }

    /** Первая строка общего зачёта — тем же порядком, которым он считается на экране истории. */
    private Long leaderOf(List<RoundResult> rows, Long groupId, SeasonService.Range range) {
        List<LeaderboardCalculator.ResultLine> lines = new ArrayList<>(rows.size());
        for (RoundResult r : rows) {
            lines.add(new LeaderboardCalculator.ResultLine(r.getUserId(), r.getPoints(),
                    r.getExactScores(), r.isWinner(), r.isLoser()));
        }
        List<LeaderboardCalculator.Row> board = LeaderboardCalculator.calculate(lines,
                questions.bonusPoints(groupId, range.from(), range.to()));
        return board.isEmpty() ? null : board.get(0).userId();
    }

    /**
     * Победители самого свежего тура. Строки приходят от новых к старым, поэтому берём
     * первый встреченный тур — он же последний сыгранный.
     */
    private static Set<Long> winnersOfLastRound(List<RoundResult> rows) {
        LocalDate last = rows.get(0).getRoundWeek();
        for (RoundResult r : rows) {
            if (r.getRoundWeek().isAfter(last)) {
                last = r.getRoundWeek();
            }
        }
        Set<Long> winners = new HashSet<>();
        for (RoundResult r : rows) {
            if (r.getRoundWeek().equals(last) && r.isWinner()) {
                winners.add(r.getUserId());
            }
        }
        return winners;
    }

    private static boolean inRange(LocalDate week, SeasonService.Range range) {
        if (range.from() != null && week.isBefore(range.from())) {
            return false;
        }
        return range.to() == null || !week.isAfter(range.to());
    }
}
