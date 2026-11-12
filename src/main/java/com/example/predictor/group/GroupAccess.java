package com.example.predictor.group;

import com.example.predictor.domain.AppUser;
import com.example.predictor.domain.FriendGroup;
import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupRole;
import com.example.predictor.repository.GroupMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Проверка, что пользователь состоит в группе (и, при необходимости, является её владельцем). */
@Component
public class GroupAccess {

    public record Membership(FriendGroup group, GroupMember member) {
        public boolean isOwner() {
            return member.getRole() == GroupRole.OWNER;
        }
    }

    private final GroupMemberRepository members;

    public GroupAccess(GroupMemberRepository members) {
        this.members = members;
    }

    /** Для чужой или несуществующей группы всегда один и тот же ответ 404. */
    @Transactional(readOnly = true)
    public Membership require(AppUser user, Long groupId) {
        GroupMember member = members.findMembership(groupId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
        return new Membership(member.getGroup(), member);
    }

    @Transactional(readOnly = true)
    public Membership requireOwner(AppUser user, Long groupId) {
        Membership membership = require(user, groupId);
        if (!membership.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Это действие доступно только владельцу группы");
        }
        return membership;
    }
}
