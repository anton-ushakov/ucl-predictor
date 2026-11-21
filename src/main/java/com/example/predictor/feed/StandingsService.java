package com.example.predictor.feed;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.Competition;
import com.example.predictor.domain.StandingRowEntity;
import com.example.predictor.repository.CompetitionRepository;
import com.example.predictor.repository.StandingRowRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Турнирная таблица самого турнира — не наша, а настоящая.
 *
 * Рядом с прогнозами это полезно: видно, кто в каком положении идёт, и ставить становится
 * интереснее. Берётся у источника раз в час, здесь только показывается.
 */
@Service
public class StandingsService {

    /** Форма: последние матчи в виде «В Н П», уже по-русски. */
    public record RowDto(int position, String team, String crest, int played, int won, int draw,
                         int lost, int goalsFor, int goalsAgainst, int points, String form) {
    }

    public record GroupDto(String title, List<RowDto> rows) {
    }

    public record TableDto(Long competitionId, String competition, List<GroupDto> groups,
                           Instant updatedAt, String note) {
    }

    public record CompetitionDto(Long id, String name, String shortName, boolean hasTable) {
    }

    public record TablesView(List<CompetitionDto> competitions, TableDto table, String note) {
    }

    private final CompetitionRepository competitions;
    private final StandingRowRepository standings;

    public StandingsService(CompetitionRepository competitions, StandingRowRepository standings) {
        this.competitions = competitions;
        this.standings = standings;
    }

    @Transactional(readOnly = true)
    public TablesView view(AppUser user, Long competitionId) {
        List<Competition> all = competitions.findByEnabledTrueOrderBySortOrderAscNameAsc();
        List<Long> ids = all.stream().map(Competition::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (StandingRowEntity row : standings.findByCompetitionIdIn(ids)) {
            counts.merge(row.getCompetitionId(), 1, Integer::sum);
        }

        List<CompetitionDto> dtos = new ArrayList<>();
        Competition chosen = null;
        for (Competition c : all) {
            boolean has = counts.getOrDefault(c.getId(), 0) > 0;
            if (has) {
                dtos.add(new CompetitionDto(c.getId(), c.getName(), c.getShortName(), true));
                if (chosen == null && (competitionId == null || c.getId().equals(competitionId))) {
                    chosen = c;
                }
            }
        }
        if (chosen == null) {
            return new TablesView(dtos, null,
                    "Таблиц пока нет: они приходят от источника раз в час и только для "
                            + "турниров, которые загружаются автоматически.");
        }
        return new TablesView(dtos, table(chosen), null);
    }

    private TableDto table(Competition competition) {
        Map<String, List<RowDto>> byGroup = new LinkedHashMap<>();
        Instant updated = null;
        for (StandingRowEntity row
                : standings.findByCompetitionIdOrderByGroupLabelAscPositionAsc(competition.getId())) {
            String group = row.getGroupLabel() == null ? "" : row.getGroupLabel();
            byGroup.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new RowDto(row.getPosition(), row.getTeamName(), row.getCrest(),
                            row.getPlayed(), row.getWon(), row.getDraw(), row.getLost(),
                            row.getGoalsFor(), row.getGoalsAgainst(), row.getPoints(),
                            form(row.getForm())));
            if (updated == null || row.getUpdatedAt().isAfter(updated)) {
                updated = row.getUpdatedAt();
            }
        }
        List<GroupDto> groups = new ArrayList<>();
        for (Map.Entry<String, List<RowDto>> e : byGroup.entrySet()) {
            groups.add(new GroupDto(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()));
        }
        return new TableDto(competition.getId(), competition.getName(), groups, updated,
                "Таблица турнира приходит от источника и обновляется раз в час.");
    }

    /** «W,W,D,L,W» → «В В Н П В»: по-русски и без запятых. */
    public static String form(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(",")) {
            String letter = switch (part.trim().toUpperCase()) {
                case "W" -> "В";
                case "D" -> "Н";
                case "L" -> "П";
                default -> "";
            };
            if (!letter.isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(letter);
            }
        }
        return out.length() == 0 ? null : out.toString();
    }
}
