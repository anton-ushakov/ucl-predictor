package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "friend_groups")
public class FriendGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 16)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** За сколько минут до начала матча закрывается приём прогнозов (0: в момент начала). */
    @Column(name = "lock_minutes_before", nullable = false)
    private int lockMinutesBefore = 0;

    /** Дополнительные показатели через запятую, по порядку применения. Пусто: без них. */
    @Column(nullable = false, length = 200)
    private String tiebreakers = Tiebreaker.EXACT_SCORES.name();

    @Enumerated(EnumType.STRING)
    @Column(name = "reveal_mode", nullable = false, length = 20)
    private RevealMode revealMode = RevealMode.AFTER_OWN_SUBMIT;

    /** Прибавлять ли к счёту голы добавочного времени. */
    @Column(name = "count_extra_time", nullable = false)
    private boolean countExtraTime = false;

    /** Прибавлять ли к счёту голы серии пенальти. */
    @Column(name = "count_penalties", nullable = false)
    private boolean countPenalties = false;

    protected FriendGroup() {
    }

    public FriendGroup(String name, String inviteCode) {
        this.name = name;
        this.inviteCode = inviteCode;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_mode", nullable = false, length = 20)
    private SubmissionMode submissionMode = SubmissionMode.WHOLE_ROUND;

    public void changeSettings(int lockMinutesBefore, List<Tiebreaker> tiebreakerList, RevealMode revealMode,
                               ScoreBasis scoreBasis, SubmissionMode submissionMode) {
        this.submissionMode = submissionMode;
        this.lockMinutesBefore = lockMinutesBefore;
        this.tiebreakers = Tiebreaker.toCsv(tiebreakerList);
        this.revealMode = revealMode;
        this.countExtraTime = scoreBasis.extraTime();
        this.countPenalties = scoreBasis.penalties();
    }

    public SubmissionMode getSubmissionMode() {
        return submissionMode;
    }

    public ScoreBasis scoreBasis() {
        return new ScoreBasis(countExtraTime, countPenalties);
    }

    public void changeInviteCode(String newCode) {
        this.inviteCode = newCode;
    }

    public List<Tiebreaker> tiebreakerList() {
        return Tiebreaker.parseList(tiebreakers);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getInviteCode() { return inviteCode; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLockMinutesBefore() { return lockMinutesBefore; }
    public String getTiebreakers() { return tiebreakers; }
    public RevealMode getRevealMode() { return revealMode; }
}
