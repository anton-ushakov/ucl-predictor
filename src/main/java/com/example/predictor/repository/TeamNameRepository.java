package com.example.predictor.repository;

import com.example.predictor.domain.TeamName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamNameRepository extends JpaRepository<TeamName, Long> {

    @Query("select t from TeamName t where lower(t.original) = lower(:original)")
    Optional<TeamName> findByOriginal(@Param("original") String original);

    List<TeamName> findAllByOrderByOriginalAsc();
}
