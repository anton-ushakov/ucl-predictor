package com.example.predictor.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Выложенная версия приложения: сам apk лежит на диске, здесь — сведения о нём. */
@Entity
@Table(name = "app_releases")
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", nullable = false, length = 40)
    private String versionName;

    @Column(name = "note", length = 400)
    private String note;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected AppRelease() {
    }

    public AppRelease(int versionCode, String versionName, String note, String fileName,
                      long sizeBytes, Instant createdAt, Long createdBy) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.note = note;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    /** Ту же версию выложили заново: сведения обновляем, строку не плодим. */
    public void replace(String versionName, String note, String fileName, long sizeBytes,
                        Instant createdAt, Long createdBy) {
        this.versionName = versionName;
        this.note = note;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public String getNote() { return note; }
    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
