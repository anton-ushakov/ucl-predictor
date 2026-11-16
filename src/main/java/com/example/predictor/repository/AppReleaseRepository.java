package com.example.predictor.repository;

import com.example.predictor.domain.AppRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppReleaseRepository extends JpaRepository<AppRelease, Long> {

    /** Последняя выложенная версия. */
    Optional<AppRelease> findTopByOrderByVersionCodeDesc();

    Optional<AppRelease> findByVersionCode(int versionCode);
}
