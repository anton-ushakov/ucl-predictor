package com.example.predictor.stats;

import com.example.predictor.stats.Achievements.Achievement;
import com.example.predictor.stats.Achievements.RoundLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AchievementsTest {

    private static Achievement one(List<Achievement> all, String code) {
        return all.stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void newcomerHasNothingYet() {
        List<Achievement> all = Achievements.of(List.of());
        assertTrue(all.stream().noneMatch(Achievement::earned));
        assertEquals(0, one(all, "SNIPER").progress());
        assertEquals(3, one(all, "SNIPER").target());
    }

    @Test
    void twoWinsInARowCountOnlyWhenTheyFollowEachOther() {
        List<RoundLine> apart = List.of(
                new RoundLine(9, 2, true, false),
                new RoundLine(3, 0, false, false),
                new RoundLine(8, 1, true, false));
        assertFalse(one(Achievements.of(apart), "BACK_TO_BACK").earned());

        List<RoundLine> inARow = List.of(
                new RoundLine(9, 2, true, false),
                new RoundLine(8, 1, true, false));
        assertTrue(one(Achievements.of(inARow), "BACK_TO_BACK").earned());
    }

    @Test
    void lastPlaceBreaksTheCleanStreak() {
        List<RoundLine> rounds = List.of(
                new RoundLine(5, 0, false, false),
                new RoundLine(4, 0, false, false),
                new RoundLine(0, 0, false, true),
                new RoundLine(6, 0, false, false),
                new RoundLine(7, 1, false, false));
        Achievement clean = one(Achievements.of(rounds), "CLEAN_4");
        assertFalse(clean.earned());
        assertEquals(2, clean.progress());
    }

    @Test
    void progressIsCappedAtTheTarget() {
        List<RoundLine> many = List.of(new RoundLine(140, 30, true, false));
        Achievement exact = one(Achievements.of(many), "EXACT_20");
        assertTrue(exact.earned());
        assertEquals(20, exact.progress());
    }

    @Test
    void zeroRoundIsAnAchievementToo() {
        List<RoundLine> rounds = List.of(new RoundLine(0, 0, false, true));
        assertTrue(one(Achievements.of(rounds), "ZERO").earned());
    }
}
