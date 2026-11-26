package com.example.predictor.round;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Майки: жёлтая — лидер сезона и только он, красная — победитель последнего тура. */
class LeaderServiceTest {

    @Test
    void yellowJerseyBelongsToOnePersonOnly() {
        LeaderService.Jerseys jerseys = new LeaderService.Jerseys(2L, Set.of());

        assertTrue(jerseys.leader(2L));
        assertFalse(jerseys.leader(1L));
        assertFalse(jerseys.lastWinner(2L));
    }

    @Test
    void redJerseyCanBeSharedWhenTheRoundWasShared() {
        // тур выиграли двое: место там и правда общее, значит и майка у обоих
        LeaderService.Jerseys jerseys = new LeaderService.Jerseys(5L, Set.of(1L, 3L));

        assertTrue(jerseys.lastWinner(1L));
        assertTrue(jerseys.lastWinner(3L));
        assertFalse(jerseys.lastWinner(5L));
    }

    @Test
    void bothJerseysOnTheSamePerson() {
        LeaderService.Jerseys jerseys = new LeaderService.Jerseys(7L, Set.of(7L));

        assertTrue(jerseys.leader(7L));
        assertTrue(jerseys.lastWinner(7L));
    }

    @Test
    void nothingPlayedYetMeansNoJerseys() {
        assertFalse(LeaderService.Jerseys.NONE.leader(1L));
        assertFalse(LeaderService.Jerseys.NONE.lastWinner(1L));
    }
}
