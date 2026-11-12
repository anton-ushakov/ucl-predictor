package com.example.predictor.feed;

import com.example.predictor.common.AppTime;
import com.example.predictor.domain.FootballMatch;
import com.example.predictor.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Обновляет матчи в нашей базе по данным football-data.org. */
@Service
public class MatchSyncService {

    /** Сколько недель назад ещё интересны матчи, которых у нас в базе нет. */
    private static final int WEEKS_BACK = 2;

    public record Result(int fetched, int created, int updated, int skipped) {
    }

    private final FootballDataClient client;
    private final MatchRepository matches;
    private final AppTime time;

    public MatchSyncService(FootballDataClient client, MatchRepository matches, AppTime time) {
        this.client = client;
        this.matches = matches;
        this.time = time;
    }

    @Transactional
    public Result sync() {
        FeedDtos.Response response = client.fetchMatches();
        List<FeedDtos.Match> feed = response == null || response.matches() == null
                ? List.of() : response.matches();

        Map<Long, FeedMapper.Mapped> mapped = new LinkedHashMap<>();
        for (FeedDtos.Match m : feed) {
            FeedMapper.map(m, time.zone()).ifPresent(x -> mapped.put(x.externalId(), x));
        }

        Map<Long, FootballMatch> existing = new HashMap<>();
        for (FootballMatch m : matches.findByExternalIdIn(new ArrayList<>(mapped.keySet()))) {
            existing.put(m.getExternalId(), m);
        }

        LocalDate from = time.currentWeek().minusWeeks(WEEKS_BACK);
        LocalDate to = time.lastPredictableWeek();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (FeedMapper.Mapped m : mapped.values()) {
            FootballMatch match = existing.get(m.externalId());
            if (match == null) {
                // новые матчи берём только из рабочего окна: ближайшие недели
                if (m.roundWeek().isBefore(from) || m.roundWeek().isAfter(to)) {
                    skipped++;
                    continue;
                }
                match = new FootballMatch(m.externalId());
                created++;
            } else {
                updated++;
            }
            FeedMapper.Parts s = m.score();
            match.applyFeed(m.homeTeam(), m.awayTeam(), m.kickoffAt(), m.roundWeek(), m.stage(),
                    m.matchday(), m.status(),
                    s == null ? null : s.homeScore(), s == null ? null : s.awayScore(),
                    s == null ? null : s.extraHome(), s == null ? null : s.extraAway(),
                    s == null ? null : s.penHome(), s == null ? null : s.penAway(),
                    s == null ? null : s.duration());
            matches.save(match);
        }
        return new Result(feed.size(), created, updated, skipped);
    }
}
