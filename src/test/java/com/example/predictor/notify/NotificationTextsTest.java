package com.example.predictor.notify;

import com.example.predictor.notify.NotificationTexts.Text;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTextsTest {

    @Test
    void lockSoonCountsMatchesCorrectly() {
        assertTrue(NotificationTexts.lockSoon("Шаурма-лига", 1).body().contains("1 матч."));
        assertTrue(NotificationTexts.lockSoon("Шаурма-лига", 3).body().contains("3 матча."));
        assertTrue(NotificationTexts.lockSoon("Шаурма-лига", 9).body().contains("9 матчей."));
        assertTrue(NotificationTexts.lockSoon("Шаурма-лига", 11).body().contains("11 матчей."));
    }

    @Test
    void matchFinishedShowsScoreAndPoints() {
        Text t = NotificationTexts.matchFinished("Шаурма-лига", "Реал", "Ювентус", 2, 1, "2:1", 3);
        assertEquals("Реал 2:1 Ювентус", t.title());
        assertTrue(t.body().contains("ваш прогноз 2:1"));
        assertTrue(t.body().contains("3 балла"));
    }

    @Test
    void matchFinishedWithoutPrediction() {
        Text t = NotificationTexts.matchFinished("Шаурма-лига", "Реал", "Ювентус", 0, 0, null, null);
        assertTrue(t.body().contains("не делали прогноз"));
    }

    @Test
    void zeroPointsIsStillCorrectRussian() {
        Text t = NotificationTexts.matchFinished("Г", "A", "B", 1, 0, "3:3", 0);
        assertTrue(t.body().contains("0 баллов"), t.body());
    }

    @Test
    void daySummaryNamesTheLeader() {
        Text t = NotificationTexts.daySummary("Шаурма-лига", LocalDate.of(2026, 11, 24), 4,
                List.of("ivan"), 7);
        assertTrue(t.title().contains("24 ноября"));
        assertTrue(t.body().contains("4 балла"));
        assertTrue(t.body().contains("Первое место в туре: ivan"));
    }

    @Test
    void daySummaryHandlesSharedFirstPlace() {
        Text t = NotificationTexts.daySummary("Г", LocalDate.of(2026, 11, 24), 0,
                List.of("ivan", "alex"), 7);
        assertTrue(t.body().contains("Делят первое место"));
        assertTrue(t.body().contains("ivan, alex"));
    }

    @Test
    void roundSummaryTellsWhoBuysShaurma() {
        Text t = NotificationTexts.roundSummary("Шаурма-лига", 2, 11,
                List.of("ivan"), List.of("alex"));
        assertTrue(t.body().contains("на 2 месте"));
        assertTrue(t.body().contains("покупает alex"));
        assertTrue(t.body().contains("угощает ivan"));
    }

    @Test
    void roundSummaryWithoutWinnersSaysSo() {
        Text t = NotificationTexts.roundSummary("Г", 1, 5, List.of(), List.of());
        assertTrue(t.body().contains("поровну"));
    }

    @Test
    void nonParticipantGetsTheResultWithoutHisPlace() {
        Text t = NotificationTexts.roundSummaryForNonParticipant("Г", List.of("ivan"), List.of("alex"));
        assertTrue(t.body().contains("не участвовали"));
        assertTrue(t.body().contains("покупает alex"));
    }
}
