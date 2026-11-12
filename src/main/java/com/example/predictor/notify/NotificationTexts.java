package com.example.predictor.notify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Тексты оповещений. Чистая логика без базы, чтобы её можно было проверить тестами. */
public final class NotificationTexts {

    public record Text(String title, String body) {
    }

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", RU);

    private NotificationTexts() {
    }

    /** Час до закрытия приёма: присылаем только тому, кто ещё не сохранил прогноз. */
    public static Text lockSoon(String groupName, int matchesLeft) {
        return new Text(
                "Через час закрывается приём прогнозов",
                groupName + ": остался час, чтобы сохранить прогноз на "
                        + matchesLeft + " " + matchWord(matchesLeft) + ".");
    }

    /**
     * Итог матча. prediction: ваш прогноз вида «2:0», null — прогноза не было.
     * points: сколько баллов принёс матч.
     */
    public static Text matchFinished(String groupName, String homeTeam, String awayTeam,
                                     int homeScore, int awayScore, String prediction, Integer points) {
        String title = homeTeam + " " + homeScore + ":" + awayScore + " " + awayTeam;
        String body = prediction == null
                ? groupName + ": вы не делали прогноз на этот матч."
                : groupName + ": ваш прогноз " + prediction + " — "
                        + points + " " + pointWord(points == null ? 0 : points) + ".";
        return new Text(title, body);
    }

    /** Итоги игрового дня: сколько набрали вы и кто идёт первым в туре. */
    public static Text daySummary(String groupName, LocalDate day, int myPoints, List<String> leaders,
                                  int leaderPoints) {
        String title = "Итоги дня, " + DAY.format(day);
        StringBuilder body = new StringBuilder(groupName + ": вы набрали за день "
                + myPoints + " " + pointWord(myPoints) + ".");
        if (!leaders.isEmpty()) {
            body.append(leaders.size() == 1 ? " Первое место в туре: " : " Делят первое место в туре: ")
                    .append(String.join(", ", leaders))
                    .append(" (").append(leaderPoints).append(" ").append(pointWord(leaderPoints)).append(").");
        }
        return new Text(title, body.toString());
    }

    /** Итоги тура: место, баллы и кто кому покупает шаурму. */
    public static Text roundSummary(String groupName, int myPlace, int myPoints,
                                    List<String> winners, List<String> losers) {
        String title = "Итоги тура подведены";
        StringBuilder body = new StringBuilder(groupName + ": вы на " + myPlace + " месте, "
                + myPoints + " " + pointWord(myPoints) + ".");
        if (!winners.isEmpty() && !losers.isEmpty()) {
            body.append(" Шаурму покупает ").append(String.join(", ", losers))
                    .append(" и угощает ").append(String.join(", ", winners)).append(".");
        } else {
            body.append(" Все набрали поровну: победителей и проигравших нет.");
        }
        return new Text(title, body.toString());
    }

    /** Итоги тура для того, кто в нём не участвовал. */
    public static Text roundSummaryForNonParticipant(String groupName, List<String> winners,
                                                     List<String> losers) {
        StringBuilder body = new StringBuilder(groupName + ": вы не делали прогнозов, поэтому в туре "
                + "не участвовали.");
        if (!winners.isEmpty() && !losers.isEmpty()) {
            body.append(" Шаурму покупает ").append(String.join(", ", losers))
                    .append(" и угощает ").append(String.join(", ", winners)).append(".");
        }
        return new Text("Итоги тура подведены", body.toString());
    }

    static String matchWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "матч";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "матча";
        }
        return "матчей";
    }

    static String pointWord(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "балл";
        }
        if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14)) {
            return "балла";
        }
        return "баллов";
    }
}
