package com.example.predictor.notify;

import com.example.predictor.notify.NotificationTexts.Text;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Текст оповещения об обсуждении тура. */
class DiscussionTextTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);

    @Test
    void bodyNamesGroupAuthorAndMessage() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, "Иван", "ну и как это понимать");
        assertEquals("Лига друзей · Иван: ну и как это понимать", text.body());
    }

    @Test
    void longMessageIsCutOff() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, "Иван", "а".repeat(300));
        assertTrue(text.body().length() < 200, "тело оповещения: " + text.body().length());
        assertTrue(text.body().endsWith("…"), text.body());
    }

    @Test
    void newlinesDoNotBreakTheLine() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, "Иван", "первая\nвторая");
        assertTrue(text.body().contains("первая вторая"), text.body());
    }

    @Test
    void titleNamesTheRound() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, "Иван", "привет");
        assertTrue(text.title().contains("Обсуждение тура"), text.title());
        assertTrue(text.title().contains("9 ноября"), text.title());
    }

    @Test
    void withoutTextOnlyGroupAndAuthorAreShown() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, "Иван", null);
        assertEquals("Лига друзей · Иван", text.body());
    }
}
