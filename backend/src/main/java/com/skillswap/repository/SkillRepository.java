package com.skillswap.repository;

import com.skillswap.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    @Query("SELECT DISTINCT s.category FROM Skill s ORDER BY s.category")
    List<String> findDistinctCategories();
}
