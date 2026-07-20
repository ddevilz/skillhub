package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "sessions")
public class Session {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Long teacherUserId;

    @Column(nullable = false)
    private Long learnerUserId;

    @Column(nullable = false)
    private Long scheduledByUserId;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionMode mode;

    @Column(length = 255)
    private String locationOrLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long v) { this.matchId = v; }
    public Long getTeacherUserId() { return teacherUserId; }
    public void setTeacherUserId(Long v) { this.teacherUserId = v; }
    public Long getLearnerUserId() { return learnerUserId; }
    public void setLearnerUserId(Long v) { this.learnerUserId = v; }
    public Long getScheduledByUserId() { return scheduledByUserId; }
    public void setScheduledByUserId(Long v) { this.scheduledByUserId = v; }
    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate v) { this.sessionDate = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public SessionMode getMode() { return mode; }
    public void setMode(SessionMode v) { this.mode = v; }
    public String getLocationOrLink() { return locationOrLink; }
    public void setLocationOrLink(String v) { this.locationOrLink = v; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus v) { this.status = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
