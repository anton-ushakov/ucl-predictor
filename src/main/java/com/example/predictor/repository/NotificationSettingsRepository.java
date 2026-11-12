package com.example.predictor.repository;

import com.example.predictor.domain.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    List<NotificationSettings> findByUserIdIn(Collection<Long> userIds);
}
