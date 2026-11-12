package com.example.predictor.round;

import com.example.predictor.round.RoundParticipants.Member;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundParticipantsTest {

    private static final Instant T = Instant.parse("2026-11-03T17:45:00Z");

    @Test
    void onlyThoseWhoPredictedParticipate() {
        List<Member> members = List.of(new Member(1, T), new Member(2, T.plusSeconds(10)), new Member(3, T));
        assertEquals(List.of(1L, 2L), RoundParticipants.select(members, Set.of(1L, 2L)));
    }

    @Test
    void nobodyPredictedMeansNobodyParticipates() {
        assertEquals(List.of(), RoundParticipants.select(List.of(new Member(1, T)), Set.of()));
    }

    @Test
    void predictorWhoLeftTheGroupIsIgnored() {
        assertEquals(List.of(2L), RoundParticipants.select(List.of(new Member(2, T)), Set.of(2L, 99L)));
    }

    @Test
    void orderedByJoinTimeThenId() {
        List<Member> members = List.of(new Member(5, T.plusSeconds(5)), new Member(4, T), new Member(3, T));
        assertEquals(List.of(3L, 4L, 5L), RoundParticipants.select(members, Set.of(3L, 4L, 5L)));
    }
}
