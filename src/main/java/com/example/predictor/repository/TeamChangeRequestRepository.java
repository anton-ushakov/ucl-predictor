package com.example.predictor.repository;

import com.example.predictor.domain.TeamChangeRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamChangeRequestRepository extends JpaRepository<TeamChangeRequest, Long> {

    List<TeamChangeRequest> findByStatusOrderByIdAsc(TeamChangeRequest.Status status);

    long countByStatus(TeamChangeRequest.Status status);
}
