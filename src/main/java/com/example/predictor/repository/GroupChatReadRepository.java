package com.example.predictor.repository;

import com.example.predictor.domain.GroupChatRead;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupChatReadRepository extends JpaRepository<GroupChatRead, Long> {

    Optional<GroupChatRead> findByGroupIdAndUserId(Long groupId, Long userId);
}
