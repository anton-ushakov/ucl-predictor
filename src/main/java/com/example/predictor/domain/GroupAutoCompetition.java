package com.example.predictor.domain;

import jakarta.persistence.*;

/** Турнир, матчи которого попадают в тур группы сами, без ручного выбора владельцем. */
@Entity
@Table(name = "group_auto_competitions")
public class GroupAutoCompetition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "competition_id", nullable = false, updatable = false)
    private Long competitionId;

    protected GroupAutoCompetition() {
    }

    public GroupAutoCompetition(Long groupId, Long competitionId) {
        this.groupId = groupId;
        this.competitionId = competitionId;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getCompetitionId() { return competitionId; }
}
