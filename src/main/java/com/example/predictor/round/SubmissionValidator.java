package com.example.predictor.round;

import com.example.predictor.domain.MatchStatus;
import com.example.predictor.domain.SubmissionMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Проверка пачки прогнозов на тур. Всё или ничего: если что-то не так, не сохраняется ничего.
 * Сколько матчей нужно указать за раз, задаёт режим сдачи (SubmissionMode):
 * весь тур целиком, игровой день целиком или любой матч отдельно.
 */
public final class SubmissionValidator {

    /**
     * Состояние матча тура с точки зрения конкретного участника.
     * day: календарный день матча по времени группы, нужен для режима «по игровым дням».
     */
    public record MatchState(long matchId, String title, MatchStatus status, Instant lockAt,
                             boolean alreadyPredicted, LocalDate day) {
    }

    public record Entry(long matchId, int homeGoals, int awayGoals) {
    }

    public static final class SubmissionException extends RuntimeException {
        private final int status;

        public SubmissionException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private SubmissionValidator() {
    }

    /** Открытые матчи выбранной части тура, на которые прогноз не указан. */
    private static List<String> openTitles(Instant now, List<MatchState> roundMatches, Set<Long> seen,
                                           Predicate<MatchState> part) {
        return roundMatches.stream()
                .filter(m -> isOpen(now, m))
                .filter(part)
                .filter(m -> !seen.contains(m.matchId()))
                .map(MatchState::title)
                .toList();
    }

    /** На матч ещё можно сделать прогноз. */
    public static boolean isOpen(Instant now, MatchState m) {
        return m.status() == MatchStatus.SCHEDULED && now.isBefore(m.lockAt()) && !m.alreadyPredicted();
    }

    public static void validate(Instant now, boolean confirmed, List<MatchState> roundMatches,
                                List<Entry> entries, SubmissionMode mode) {
        if (!confirmed) {
            throw new SubmissionException(400,
                    "Подтвердите сохранение: после сохранения прогнозы изменить нельзя");
        }
        if (entries == null || entries.isEmpty()) {
            throw new SubmissionException(400, "Не указано ни одного прогноза");
        }

        Map<Long, MatchState> byId = new HashMap<>();
        for (MatchState m : roundMatches) {
            byId.put(m.matchId(), m);
        }

        Set<Long> seen = new HashSet<>();
        for (Entry e : entries) {
            if (!seen.add(e.matchId())) {
                throw new SubmissionException(400, "Один и тот же матч указан дважды");
            }
            MatchState m = byId.get(e.matchId());
            if (m == null) {
                throw new SubmissionException(400, "Матч не относится к этому туру");
            }
            if (e.homeGoals() < 0 || e.awayGoals() < 0) {
                throw new SubmissionException(400, "Число голов не может быть отрицательным");
            }
            if (m.alreadyPredicted()) {
                throw new SubmissionException(409,
                        "Прогноз на матч «" + m.title() + "» уже сохранён и не может быть изменён");
            }
            if (m.status() != MatchStatus.SCHEDULED) {
                throw new SubmissionException(409,
                        "Матч «" + m.title() + "» уже идёт, сыгран, перенесён или отменён: прогноз принять нельзя");
            }
            if (!now.isBefore(m.lockAt())) {
                throw new SubmissionException(409,
                        "Приём прогнозов на матч «" + m.title() + "» уже закрыт");
            }
        }

        switch (mode) {
            case WHOLE_ROUND -> {
                // весь тур сразу: нужны все открытые матчи недели
                List<String> missing = openTitles(now, roundMatches, seen, m -> true);
                if (!missing.isEmpty()) {
                    throw new SubmissionException(400,
                            "В этой группе прогнозы сдаются на весь тур сразу. Не хватает матчей: "
                                    + String.join("; ", missing));
                }
            }
            case BY_DAY -> {
                // день сдаётся целиком, но дней за раз можно сдать сколько угодно
                Set<LocalDate> days = new HashSet<>();
                for (Entry e : entries) {
                    days.add(byId.get(e.matchId()).day());
                }
                List<String> missing = openTitles(now, roundMatches, seen, m -> days.contains(m.day()));
                if (!missing.isEmpty()) {
                    throw new SubmissionException(400,
                            "В этой группе игровой день сдаётся целиком. Не хватает матчей: "
                                    + String.join("; ", missing));
                }
            }
            case FREE -> {
                // свободный режим: достаточно хотя бы одного матча
            }
        }
    }
}
