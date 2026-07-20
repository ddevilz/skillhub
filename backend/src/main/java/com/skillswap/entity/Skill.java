package com.skillswap.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skill")
public class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String skillName;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String v) { this.skillName = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
}
