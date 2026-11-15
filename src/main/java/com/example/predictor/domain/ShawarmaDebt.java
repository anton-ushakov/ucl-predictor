package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Долг по итогам тура: проигравший должен шаурму победителю. */
@Entity
@Table(name = "shawarma_debts")
public class ShawarmaDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "round_week", nullable = false, updatable = false)
    private LocalDate roundWeek;

    @Column(name = "debtor_id", nullable = false, updatable = false)
    private Long debtorId;

    @Column(name = "creditor_id", nullable = false, updatable = false)
    private Long creditorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by")
    private Long settledBy;

    protected ShawarmaDebt() {
    }

    public ShawarmaDebt(Long groupId, LocalDate roundWeek, Long debtorId, Long creditorId,
                        Instant createdAt) {
        this.groupId = groupId;
        this.roundWeek = roundWeek;
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.createdAt = createdAt;
    }

    public void settle(Long by, Instant at) {
        this.settledAt = at;
        this.settledBy = by;
    }

    /** Отметку поставили по ошибке: долг снова открыт. */
    public void reopen() {
        this.settledAt = null;
        this.settledBy = null;
    }

    public boolean isSettled() {
        return settledAt != null;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getRoundWeek() { return roundWeek; }
    public Long getDebtorId() { return debtorId; }
    public Long getCreditorId() { return creditorId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
    public Long getSettledBy() { return settledBy; }
}
