package com.example.predictor.repository;

import com.example.predictor.domain.FriendGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {

    Optional<FriendGroup> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
