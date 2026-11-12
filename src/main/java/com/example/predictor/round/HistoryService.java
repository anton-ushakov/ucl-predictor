package com.example.predictor.round;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.RoundResult;
import com.example.predictor.domain.ScoreBasis;
import com.example.predictor.group.GroupAccess;
import com.example.predictor.repository.GroupMemberRepository;
import com.example.predictor.repository.RoundResultRepository;
import com.example.predictor.standings.LeaderboardCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** История завершённых туров и общий зачёт группы. */
@Service
public class HistoryService {

    public record ResultRow(Long userId, String username, int place, int points, int exactScores,
                            int outcomeHits, int goalDiffHits, int goalError,
                            boolean winner, boolean loser) {
    }

    /** buyers: проигравшие (покупают), receivers: победители (получают). */
    public record HistoryRound(LocalDate week, Instant finalizedAt, List<String> tiebreakers,
                               boolean countExtraTime, boolean countPenalties, String scoreDescription,
                               List<ResultRow> rows, List<String> buyers, List<String> receivers) {
    }

    public record LeaderRow(Long userId, String username, int place, int rounds, int totalPoints,
                            int exactScores, int wins, int losses) {
    }

    private final GroupAccess access;
    private final RoundResultRepository results;
    private final GroupMemberRepository members;

    public HistoryService(GroupAccess access, RoundResultRepository results, GroupMemberRepository members) {
        this.access = access;
        this.results = results;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<HistoryRound> history(AppUser user, Long groupId) {
        access.require(user, groupId);
        Map<Long, String> names = names(groupId);

        // репозиторий отдаёт туры от новых к старым, внутри тура по местам
        Map<LocalDate, List<RoundResult>> byWeek = new LinkedHashMap<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            byWeek.computeIfAbsent(r.getRoundWeek(), k -> new ArrayList<>()).add(r);
        }

        List<HistoryRound> rounds = new ArrayList<>();
        for (Map.Entry<LocalDate, List<RoundResult>> e : byWeek.entrySet()) {
            List<ResultRow> rows = new ArrayList<>();
            List<String> buyers = new ArrayList<>();
            List<String> receivers = new ArrayList<>();
            for (RoundResult r : e.getValue()) {
                String name = names.getOrDefault(r.getUserId(), "?");
                rows.add(new ResultRow(r.getUserId(), name, r.getPlace(), r.getPoints(), r.getExactScores(),
                        r.getOutcomeHits(), r.getGoalDiffHits(), r.getGoalError(), r.isWinner(), r.isLoser()));
                if (r.isLoser()) {
                    buyers.add(name);
                }
                if (r.isWinner()) {
                    receivers.add(name);
                }
            }
            RoundResult first = e.getValue().get(0);
            List<String> tiebreakers = first.getTiebreakers().isBlank()
                    ? List.of() : List.of(first.getTiebreakers().split(","));
            ScoreBasis basis = first.scoreBasis();
            rounds.add(new HistoryRound(e.getKey(), first.getFinalizedAt(), tiebreakers,
                    basis.extraTime(), basis.penalties(), basis.describe(), rows, buyers, receivers));
        }
        return rounds;
    }

    @Transactional(readOnly = true)
    public List<LeaderRow> leaderboard(AppUser user, Long groupId) {
        access.require(user, groupId);
        Map<Long, String> names = names(groupId);

        List<LeaderboardCalculator.ResultLine> lines = new ArrayList<>();
        for (RoundResult r : results.findByGroupIdOrderByRoundWeekDescPlaceAscUserIdAsc(groupId)) {
            lines.add(new LeaderboardCalculator.ResultLine(r.getUserId(), r.getPoints(), r.getExactScores(),
                    r.isWinner(), r.isLoser()));
        }
        List<LeaderRow> board = new ArrayList<>();
        for (LeaderboardCalculator.Row r : LeaderboardCalculator.calculate(lines)) {
            board.add(new LeaderRow(r.userId(), names.getOrDefault(r.userId(), "?"), r.place(), r.rounds(),
                    r.totalPoints(), r.exactScores(), r.wins(), r.losses()));
        }
        return board;
    }

    private Map<Long, String> names(Long groupId) {
        Map<Long, String> names = new HashMap<>();
        for (GroupMember gm : members.findAllWithUser(groupId)) {
            names.put(gm.getUser().getId(), gm.getUser().getUsername());
        }
        return names;
    }
}
