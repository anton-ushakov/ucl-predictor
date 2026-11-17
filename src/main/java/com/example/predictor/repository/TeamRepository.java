package com.example.predictor.repository;

import com.example.predictor.domain.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByKindOrderByNameAsc(Team.Kind kind, Pageable pageable);

    @Query("select t from Team t where t.kind = :kind and lower(t.name) like lower(concat('%', :query, '%')) "
            + "order by t.name")
    List<Team> search(@Param("kind") Team.Kind kind, @Param("query") String query, Pageable pageable);

    Optional<Team> findByKindAndNameIgnoreCase(Team.Kind kind, String name);

    long countByKind(Team.Kind kind);
}
