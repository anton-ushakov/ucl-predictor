package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "group_members")
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private FriendGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupRole role = GroupRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    /** Помощник: может собирать состав тура, когда группа это разрешила. */
    @Column(name = "helper", nullable = false)
    private boolean helper = false;

    protected GroupMember() {
    }

    public GroupMember(FriendGroup group, AppUser user, GroupRole role) {
        this.id = new GroupMemberId(group.getId(), user.getId());
        this.group = group;
        this.user = user;
        this.role = role;
    }

    public void setHelper(boolean helper) {
        this.helper = helper;
    }

    public boolean isHelper() { return helper; }

    public GroupMemberId getId() { return id; }
    public FriendGroup getGroup() { return group; }
    public AppUser getUser() { return user; }
    public GroupRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
