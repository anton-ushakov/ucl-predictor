package com.example.predictor.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Проценты в голосовании считаются от числа проголосовавших. */
class PollMathTest {

    @Test
    void noVotesNoPercents() {
        assertEquals(0, PollService.percentOf(0, 0));
    }

    @Test
    void halfIsFifty() {
        assertEquals(50, PollService.percentOf(2, 4));
    }

    @Test
    void thirdIsRounded() {
        assertEquals(33, PollService.percentOf(1, 3));
        assertEquals(67, PollService.percentOf(2, 3));
    }

    @Test
    void bothSidesAddUpToHundred() {
        for (int total = 1; total <= 12; total++) {
            for (int yes = 0; yes <= total; yes++) {
                int yesPercent = PollService.percentOf(yes, total);
                int noPercent = 100 - yesPercent;
                assertEquals(100, yesPercent + noPercent, "голосов " + yes + " из " + total);
            }
        }
    }
}
