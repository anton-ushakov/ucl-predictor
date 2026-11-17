package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Порядок туров в списке: сверху тот, на который играют сейчас.
 */
class RoundOrderTest {

    private static final LocalDate CURRENT = LocalDate.of(2026, 11, 16);

    private List<LocalDate> sorted(LocalDate... weeks) {
        List<LocalDate> list = new ArrayList<>(List.of(weeks));
        list.sort(Comparator.comparingLong(w -> RoundService.distanceKey(w, CURRENT)));
        return list;
    }

    @Test
    void currentFirst() {
        List<LocalDate> order = sorted(
                CURRENT.minusWeeks(2),
                CURRENT.plusWeeks(1),
                CURRENT,
                CURRENT.minusWeeks(1));
        assertTrue(order.equals(List.of(
                CURRENT,
                CURRENT.plusWeeks(1),
                CURRENT.minusWeeks(1),
                CURRENT.minusWeeks(2))), "порядок: " + order);
    }

    @Test
    void futureAscendingPastDescending() {
        List<LocalDate> order = sorted(
                CURRENT.plusWeeks(3),
                CURRENT.minusWeeks(5),
                CURRENT.plusWeeks(1),
                CURRENT.minusWeeks(1));
        assertTrue(order.equals(List.of(
                CURRENT.plusWeeks(1),
                CURRENT.plusWeeks(3),
                CURRENT.minusWeeks(1),
                CURRENT.minusWeeks(5))), "порядок: " + order);
    }
}
