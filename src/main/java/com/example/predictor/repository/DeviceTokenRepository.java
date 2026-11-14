package com.example.predictor.repository;

import com.example.predictor.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    List<DeviceToken> findByUserIdIn(Collection<Long> userIds);

    void deleteByToken(String token);
}
