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
    void oneMessageIsCountedInSingular() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 1, "Иван", "ну и как это понимать");
        assertTrue(text.body().contains("1 новое сообщение"), text.body());
        assertTrue(text.body().startsWith("Лига друзей: "), text.body());
    }

    @Test
    void wordChangesWithCount() {
        assertEquals("новое сообщение", NotificationTexts.messageWord(1));
        assertEquals("новых сообщения", NotificationTexts.messageWord(3));
        assertEquals("новых сообщений", NotificationTexts.messageWord(5));
        assertEquals("новых сообщений", NotificationTexts.messageWord(11));
        assertEquals("новое сообщение", NotificationTexts.messageWord(21));
        assertEquals("новых сообщения", NotificationTexts.messageWord(22));
    }

    @Test
    void lastMessageIsShownAfterTheCount() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 4, "Пётр", "я же говорил");
        assertTrue(text.body().contains("4 новых сообщения"), text.body());
        assertTrue(text.body().contains("Пётр: я же говорил"), text.body());
    }

    @Test
    void longMessageIsCutOff() {
        String long_ = "а".repeat(300);
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 1, "Иван", long_);
        assertTrue(text.body().length() < 200, "тело оповещения: " + text.body().length());
        assertTrue(text.body().endsWith("…"), text.body());
    }

    @Test
    void newlinesDoNotBreakTheLine() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 1, "Иван", "первая\nвторая");
        assertTrue(text.body().contains("первая вторая"), text.body());
    }

    @Test
    void titleNamesTheRound() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 2, "Иван", "привет");
        assertTrue(text.title().contains("Обсуждение тура"), text.title());
        assertTrue(text.title().contains("9 ноября"), text.title());
    }

    @Test
    void withoutLastMessageOnlyTheCountIsShown() {
        Text text = NotificationTexts.discussion("Лига друзей", WEEK, 2, null, null);
        assertEquals("Лига друзей: 2 новых сообщения", text.body());
    }
}
