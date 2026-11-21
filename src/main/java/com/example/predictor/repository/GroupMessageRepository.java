package com.example.predictor.repository;

import com.example.predictor.domain.GroupMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    List<GroupMessage> findByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);

    List<GroupMessage> findByGroupIdAndIdGreaterThanOrderByIdAsc(Long groupId, Long afterId);

    long countByGroupIdAndDeletedAtIsNull(Long groupId);

    long countByGroupIdAndIdGreaterThanAndDeletedAtIsNull(Long groupId, Long afterId);
}
