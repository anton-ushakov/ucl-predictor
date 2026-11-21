package com.example.predictor.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.predictor.notify.NotificationTexts.Text;
import org.junit.jupiter.api.Test;

/** Текст оповещения о сообщении в чате группы. */
class ChatTextTest {

    @Test
    void titleNamesTheGroupAndBodyTheAuthor() {
        Text text = NotificationTexts.chat("Лига друзей", "Иван", "кто идёт в субботу");
        assertEquals("Чат · Лига друзей", text.title());
        assertEquals("Иван: кто идёт в субботу", text.body());
    }

    @Test
    void longMessageIsCutOff() {
        Text text = NotificationTexts.chat("Лига друзей", "Иван", "а".repeat(300));
        assertTrue(text.body().length() < 200, "тело оповещения: " + text.body().length());
        assertTrue(text.body().endsWith("…"), text.body());
    }

    @Test
    void newlinesDoNotBreakTheLine() {
        Text text = NotificationTexts.chat("Лига друзей", "Иван", "первая\nвторая");
        assertTrue(text.body().contains("первая вторая"), text.body());
    }
}
