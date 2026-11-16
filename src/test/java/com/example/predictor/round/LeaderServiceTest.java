package com.example.predictor.round;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Жёлтая майка: у кого больше всего побед в сезоне. */
class LeaderServiceTest {

    @Test
    void mostWinsGetsTheJersey() {
        assertEquals(Set.of(2L), LeaderService.topOf(Map.of(1L, 2, 2L, 5, 3L, 1)));
    }

    @Test
    void tieMeansEveryoneOnTopWearsIt() {
        assertEquals(Set.of(1L, 3L), LeaderService.topOf(Map.of(1L, 4, 2L, 2, 3L, 4)));
    }

    @Test
    void noWinsNoJersey() {
        assertTrue(LeaderService.topOf(Map.of()).isEmpty());
        assertTrue(LeaderService.topOf(Map.of(1L, 0, 2L, 0)).isEmpty());
    }
}
