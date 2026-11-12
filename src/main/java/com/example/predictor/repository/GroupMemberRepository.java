package com.example.predictor.repository;

import com.example.predictor.domain.GroupMember;
import com.example.predictor.domain.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    @Query("select m from GroupMember m join fetch m.group "
            + "where m.id.groupId = :groupId and m.id.userId = :userId")
    Optional<GroupMember> findMembership(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("select m from GroupMember m join fetch m.user "
            + "where m.id.groupId = :groupId order by m.joinedAt, m.id.userId")
    List<GroupMember> findAllWithUser(@Param("groupId") Long groupId);

    @Query("select m from GroupMember m join fetch m.group g "
            + "where m.id.userId = :userId order by g.name")
    List<GroupMember> findAllOfUser(@Param("userId") Long userId);

    @Query("select count(m) from GroupMember m where m.id.groupId = :groupId")
    long countInGroup(@Param("groupId") Long groupId);
}
