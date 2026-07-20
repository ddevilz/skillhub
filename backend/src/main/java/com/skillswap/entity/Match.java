package com.skillswap.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userAId;

    @Column(nullable = false)
    private Long userBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserAId() { return userAId; }
    public void setUserAId(Long v) { this.userAId = v; }
    public Long getUserBId() { return userBId; }
    public void setUserBId(Long v) { this.userBId = v; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus v) { this.status = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
