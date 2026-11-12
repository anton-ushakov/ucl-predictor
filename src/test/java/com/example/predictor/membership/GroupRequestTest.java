package com.example.predictor.membership;

import com.example.predictor.domain.GroupRequest;
import com.example.predictor.domain.RequestKind;
import com.example.predictor.domain.RequestStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GroupRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    @Test
    void newRequestWaitsForDecision() {
        GroupRequest r = new GroupRequest(1L, 2L, RequestKind.REQUEST, 2L, NOW);
        assertEquals(RequestStatus.PENDING, r.getStatus());
        assertNull(r.getDecidedAt());
        assertNull(r.getDecidedBy());
    }

    @Test
    void inviteIsDecidedByTheInviteeAndRequestByTheOwner() {
        assertTrue(new GroupRequest(1L, 2L, RequestKind.INVITE, 9L, NOW).decidedByInvitee());
        assertFalse(new GroupRequest(1L, 2L, RequestKind.REQUEST, 2L, NOW).decidedByInvitee());
    }

    @Test
    void decisionIsRecordedWithAuthorAndTime() {
        GroupRequest r = new GroupRequest(1L, 2L, RequestKind.INVITE, 9L, NOW);
        Instant later = NOW.plusSeconds(60);
        r.decide(RequestStatus.ACCEPTED, 2L, later);

        assertEquals(RequestStatus.ACCEPTED, r.getStatus());
        assertEquals(Long.valueOf(2), r.getDecidedBy());
        assertEquals(later, r.getDecidedAt());
        // кто создал запись, остаётся прежним
        assertEquals(Long.valueOf(9), r.getCreatedBy());
    }

    @Test
    void declineAndCancelAreRecordedTheSameWay() {
        GroupRequest declined = new GroupRequest(1L, 2L, RequestKind.REQUEST, 2L, NOW);
        declined.decide(RequestStatus.DECLINED, 9L, NOW);
        assertEquals(RequestStatus.DECLINED, declined.getStatus());

        GroupRequest cancelled = new GroupRequest(1L, 2L, RequestKind.INVITE, 9L, NOW);
        cancelled.decide(RequestStatus.CANCELLED, 9L, NOW);
        assertEquals(RequestStatus.CANCELLED, cancelled.getStatus());
    }
}
