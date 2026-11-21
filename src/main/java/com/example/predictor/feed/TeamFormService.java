package com.example.predictor.feed;

import com.example.predictor.domain.FootballMatch;
import com.example.predictor.repository.MatchRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Форма команды по нашим же матчам.
 *
 * Источник присылает форму вместе с турнирной таблицей, но не для всех турниров: у кубков
 * и у молодых таблиц это поле пустое. Тогда считаем сами — все сыгранные матчи у нас и так
 * лежат, и по ним видно, кто как идёт.
 */
@Service
public class TeamFormService {

    /** Сколько последних матчей показываем: пять кружков читаются одним взглядом. */
    private static final int DEPTH = 5;

    private final MatchRepository matches;

    public TeamFormService(MatchRepository matches) {
        this.matches = matches;
    }

    /** Сыгранный матч в том виде, в каком он нужен подсчёту формы. */
    public record Played(String home, String away, Integer homeGoals, Integer awayGoals) {
    }

    /**
     * Форма для каждой названной команды: строка вида «WWDLW», свежий матч последним.
     * Команды без сыгранных матчей в ответе просто отсутствуют.
     */
    @Transactional(readOnly = true)
    public Map<String, String> formOf(Collection<String> teams) {
        if (teams == null || teams.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Played> played = new ArrayList<>();
        for (FootballMatch m : matches.findFinishedOf(teams)) {
            played.add(new Played(m.getHomeTeam(), m.getAwayTeam(), m.getHomeScore(),
                    m.getAwayScore()));
        }
        return build(teams, played);
    }

    /** Матчи идут от свежих к старым, поэтому буквы дописываются слева. */
    static Map<String, String> build(Collection<String> teams, List<Played> newestFirst) {
        Map<String, StringBuilder> letters = new HashMap<>();
        for (Played m : newestFirst) {
            addLetter(letters, teams, m.home(), m, true);
            addLetter(letters, teams, m.away(), m, false);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> e : letters.entrySet()) {
            if (e.getValue().length() > 0) {
                result.put(e.getKey(), e.getValue().toString());
            }
        }
        return result;
    }

    private static void addLetter(Map<String, StringBuilder> letters, Collection<String> teams,
                                  String team, Played m, boolean atHome) {
        if (team == null || !teams.contains(team)) {
            return;
        }
        StringBuilder form = letters.computeIfAbsent(team, k -> new StringBuilder());
        if (form.length() >= DEPTH) {
            return;
        }
        if (m.homeGoals() == null || m.awayGoals() == null) {
            return;
        }
        char letter;
        if (m.homeGoals().equals(m.awayGoals())) {
            letter = 'D';
        } else {
            boolean homeWon = m.homeGoals() > m.awayGoals();
            letter = homeWon == atHome ? 'W' : 'L';
        }
        form.insert(0, letter);
    }
}
