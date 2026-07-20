package com.skillswap.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_credit")
public class SkillCredit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private int totalCredits = 10;

    @Column(nullable = false)
    private int creditsEarned = 0;

    @Column(nullable = false)
    private int creditsSpent = 0;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    public SkillCredit() {}
    public SkillCredit(Long userId) { this.userId = userId; }

    @PrePersist @PreUpdate
    void touch() { this.lastUpdated = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public int getTotalCredits() { return totalCredits; }
    public void setTotalCredits(int v) { this.totalCredits = v; }
    public int getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(int v) { this.creditsEarned = v; }
    public int getCreditsSpent() { return creditsSpent; }
    public void setCreditsSpent(int v) { this.creditsSpent = v; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
}
