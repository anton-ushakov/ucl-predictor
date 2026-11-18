package com.example.predictor.repository;

import com.example.predictor.domain.GroupRound;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRoundRepository extends JpaRepository<GroupRound, Long> {

    Optional<GroupRound> findByGroupIdAndRoundKey(Long groupId, LocalDate roundKey);

    List<GroupRound> findByGroupIdAndRoundKeyBetweenOrderByRoundKeyAsc(Long groupId,
                                                                      LocalDate from,
                                                                      LocalDate to);

    List<GroupRound> findByGroupIdOrderByRoundKeyDesc(Long groupId);

    boolean existsByGroupIdAndRoundKey(Long groupId, LocalDate roundKey);
}
