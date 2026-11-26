package com.example.predictor.repository;

import com.example.predictor.domain.RoundExhibition;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundExhibitionRepository extends JpaRepository<RoundExhibition, Long> {

    List<RoundExhibition> findByGroupIdAndRoundKey(Long groupId, LocalDate roundKey);

    Optional<RoundExhibition> findByGroupIdAndRoundKeyAndUserId(Long groupId, LocalDate roundKey,
                                                                Long userId);
}
