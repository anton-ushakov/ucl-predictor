package com.example.predictor.stats;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.ShawarmaDebt;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.repository.PredictionRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.repository.ShawarmaDebtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Личная статистика участника в группе. Считается из уже сохранённых итогов туров,
 * поэтому ничего не хранит отдельно и всегда совпадает с историей.
 */
@Service
public class StatsService {

    /**
     * bestWeek и bestPoints: лучший тур. favouriteScore: счёт, который участник ставит чаще
     * других. shawarmaBought / shawarmaReceived: купленные и полученные шаурмы.
     */
    public record StatsView(int rounds, int totalPoints, String averagePoints, int exactScores,
                            int outcomeHits, int wins, int losses, LocalDate bestWeek,
                            int bestPoints, int predictions, String favouriteScore,
                            int shawarmaBought, int shawarmaReceived, int debtsOpen, String note,
                            List<Achievements.Achievement> achievements, int earned) {
    }

    private final GroupAccess access;
    private final RoundResultRepository results;
    private final PredictionRepository predictions;
    private final ShawarmaDebtRepository debts;

    public StatsService(GroupAccess access, RoundResultRepository results,
                        PredictionRepository predictions, ShawarmaDebtRepository debts) {
        this.access = access;
        this.results = results;
        this.predictions = predictions;
        this.debts = debts;
    }

    @Transactional(readOnly = true)
    public StatsView forUser(AppUser user, Long groupId) {
        access.require(user, groupId);
        Long me = user.getId();

        // итоги туров по порядку: по ним считаются и числа, и достижения
        List<Achievements.RoundLine> lines = new ArrayList<>();
        int rounds = 0;
        int points = 0;
        int exact = 0;
        int outcome = 0;
        int wins = 0;
        int losses = 0;
        LocalDate bestWeek = null;
        int bestPoints = 0;
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            if (!r.getUserId().equals(me)) {
                continue;
            }
            rounds++;
            points += r.getPoints();
            exact += r.getExactScores();
            outcome += r.getOutcomeHits();
            if (r.isWinner()) {
                wins++;
            }
            if (r.isLoser()) {
                losses++;
            }
            if (bestWeek == null || r.getPoints() > bestPoints) {
                bestWeek = r.getRoundWeek();
                bestPoints = r.getPoints();
            }
            lines.add(new Achievements.RoundLine(r.getPoints(), r.getExactScores(),
                    r.isWinner(), r.isLoser()));
        }
        // в базе туры идут от свежих к старым, а серии считаются от старых к свежим
        java.util.Collections.reverse(lines);

        List<Object[]> goals = predictions.findGoalsOfUser(groupId, me);
        Map<String, Integer> byScore = new HashMap<>();
        for (Object[] row : goals) {
            String score = row[0] + ":" + row[1];
            byScore.merge(score, 1, Integer::sum);
        }
        String favourite = "";
        int favouriteCount = 0;
        for (Map.Entry<String, Integer> e : byScore.entrySet()) {
            if (e.getValue() > favouriteCount) {
                favourite = e.getKey();
                favouriteCount = e.getValue();
            }
        }

        int bought = 0;
        int received = 0;
        int open = 0;
        for (ShawarmaDebt d : debts.findByGroupIdOrderByRoundWeekDescIdAsc(groupId)) {
            if (d.getDebtorId().equals(me)) {
                bought++;
                if (!d.isSettled()) {
                    open++;
                }
            } else if (d.getCreditorId().equals(me)) {
                received++;
            }
        }

        String average = rounds == 0 ? "0"
                : String.format(java.util.Locale.ROOT, "%.1f", (double) points / rounds);
        String note = rounds == 0
                ? "Статистика появится, когда подведут итоги первого тура."
                : "Считается по завершённым турам этой группы.";
        List<Achievements.Achievement> achievements = Achievements.of(lines, bought, received);
        int earned = 0;
        for (Achievements.Achievement a : achievements) {
            if (a.earned()) {
                earned++;
            }
        }
        return new StatsView(rounds, points, average, exact, outcome, wins, losses, bestWeek,
                bestPoints, goals.size(), favourite, bought, received, open, note,
                achievements, earned);
    }
}
