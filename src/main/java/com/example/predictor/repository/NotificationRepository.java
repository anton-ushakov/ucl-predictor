package com.example.predictor.repository;

import com.example.predictor.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Уже отправленные оповещения с такими ключами: чтобы не слать одно и то же дважды. */
    @Query("select n from Notification n where n.userId in :userIds and n.dedupKey in :keys")
    List<Notification> findExisting(@Param("userIds") Collection<Long> userIds,
                                    @Param("keys") Collection<String> keys);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now "
            + "where n.userId = :userId and n.id in :ids and n.readAt is null")
    int markRead(@Param("userId") Long userId, @Param("ids") Collection<Long> ids,
                 @Param("now") Instant now);
}
