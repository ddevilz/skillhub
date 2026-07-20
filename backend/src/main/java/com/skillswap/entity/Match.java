package com.skillswap.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id", nullable = false)
    private Long userAId;

    @Column(name = "user_b_id", nullable = false)
    private Long userBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.PENDING;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP") // mirrors V2__skills_matching.sql DEFAULT CURRENT_TIMESTAMP; only affects
    // Hibernate's ddl-auto=create-drop test schema (Flyway owns the real schema, ddl-auto=none there).
    // Needed so raw-JDBC test inserts (bypassing @PrePersist) satisfy the NOT NULL column.
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
