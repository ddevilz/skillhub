package com.skillswap.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_skill")
public class UserSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillType skillType;

    @Column(length = 50)
    private String experience;

    @Column(length = 20)
    private String proficiency;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long v) { this.skillId = v; }
    public SkillType getSkillType() { return skillType; }
    public void setSkillType(SkillType v) { this.skillType = v; }
    public String getExperience() { return experience; }
    public void setExperience(String v) { this.experience = v; }
    public String getProficiency() { return proficiency; }
    public void setProficiency(String v) { this.proficiency = v; }
}
