package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Приглашение в группу или заявка на вступление. Кто принимает решение, зависит от kind:
 * приглашение решает приглашённый, заявку — владелец группы.
 */
@Entity
@Table(name = "group_requests")
public class GroupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private RequestKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RequestStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    protected GroupRequest() {
    }

    public GroupRequest(Long groupId, Long userId, RequestKind kind, Long createdBy, Instant createdAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.kind = kind;
        this.status = RequestStatus.PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void decide(RequestStatus status, Long decidedBy, Instant at) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = at;
    }

    /** Кто вправе принять решение: приглашение решает приглашённый, заявку — владелец. */
    public boolean decidedByInvitee() {
        return kind == RequestKind.INVITE;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public RequestKind getKind() { return kind; }
    public RequestStatus getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Long getDecidedBy() { return decidedBy; }
}
