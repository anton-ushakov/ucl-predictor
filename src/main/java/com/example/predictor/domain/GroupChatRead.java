package com.example.predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** До какого сообщения участник дочитал чат группы. */
@Entity
@Table(name = "group_chat_reads")
public class GroupChatRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_id", nullable = false)
    private Long lastReadId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupChatRead() {
    }

    public GroupChatRead(Long groupId, Long userId, Long lastReadId, Instant updatedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.lastReadId = lastReadId;
        this.updatedAt = updatedAt;
    }

    public Long getLastReadId() {
        return lastReadId;
    }

    /** Назад не отматываем: прочитанное прочитано. */
    public void readUpTo(Long messageId, Instant when) {
        if (messageId != null && messageId > lastReadId) {
            this.lastReadId = messageId;
            this.updatedAt = when;
        }
    }
}
