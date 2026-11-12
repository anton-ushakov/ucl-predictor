package com.example.predictor.repository;

import com.example.predictor.domain.GroupRequest;
import com.example.predictor.domain.RequestKind;
import com.example.predictor.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRequestRepository extends JpaRepository<GroupRequest, Long> {

    Optional<GroupRequest> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, RequestStatus status);

    /** Нерешённые приглашения, адресованные участнику. */
    List<GroupRequest> findByUserIdAndKindAndStatusOrderByCreatedAtDesc(
            Long userId, RequestKind kind, RequestStatus status);

    /** Нерешённые заявки и приглашения группы. */
    List<GroupRequest> findByGroupIdAndStatusOrderByCreatedAtDesc(Long groupId, RequestStatus status);

    /** Все нерешённые записи участника: чтобы показать состояние в списке групп. */
    List<GroupRequest> findByUserIdAndStatus(Long userId, RequestStatus status);
}
