package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_badge")
public class SkillBadge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 20)
    private BadgeType badgeType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime awardedDate;

    @PrePersist
    void onCreate() { if (awardedDate == null) awardedDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long v) { this.skillId = v; }
    public BadgeType getBadgeType() { return badgeType; }
    public void setBadgeType(BadgeType v) { this.badgeType = v; }
    public LocalDateTime getAwardedDate() { return awardedDate; }
}
