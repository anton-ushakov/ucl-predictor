package com.example.predictor.repository;

import com.example.predictor.domain.ShawarmaDebt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShawarmaDebtRepository extends JpaRepository<ShawarmaDebt, Long> {

    List<ShawarmaDebt> findByGroupIdOrderByRoundWeekDescIdAsc(Long groupId);

    /** Неотданные долги участника во всех его группах. */
    List<ShawarmaDebt> findByDebtorIdAndSettledAtIsNull(Long debtorId);

    /** Неотданные долги перед участником. */
    List<ShawarmaDebt> findByCreditorIdAndSettledAtIsNull(Long creditorId);

    boolean existsByGroupIdAndRoundWeekAndDebtorIdAndCreditorId(
            Long groupId, LocalDate roundWeek, Long debtorId, Long creditorId);
}
