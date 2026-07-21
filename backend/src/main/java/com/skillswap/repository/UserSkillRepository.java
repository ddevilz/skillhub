package com.skillswap.repository;

import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserId(Long userId);
    List<UserSkill> findByUserIdAndSkillType(Long userId, SkillType skillType);
    long countByUserIdAndSkillType(Long userId, SkillType skillType);
    Optional<UserSkill> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndSkillIdAndSkillType(Long userId, Long skillId, SkillType skillType);
    boolean existsBySkillId(Long skillId);

    // Candidates who CAN_TEACH a skill the current user WANT_TO_LEARN.
    // Empty-string sentinels are used instead of NULL so the bind params stay typed on H2.
    @Query(value = """
        SELECT v.id AS userId, v.full_name AS fullName, v.city AS city,
               COUNT(DISTINCT vt.skill_id) AS matchedSkills
        FROM users v
        JOIN user_skill vt ON vt.user_id = v.id AND vt.skill_type = 'CAN_TEACH'
        JOIN user_skill ul ON ul.skill_id = vt.skill_id
             AND ul.user_id = :userId AND ul.skill_type = 'WANT_TO_LEARN'
        JOIN skill s ON s.id = vt.skill_id
        WHERE v.id <> :userId AND v.active = TRUE
          AND (:city = '' OR v.city = :city)
          AND (:category = '' OR s.category = :category)
        GROUP BY v.id, v.full_name, v.city
        ORDER BY matchedSkills DESC
        """, nativeQuery = true)
    List<MatchProjection> findSuggestions(@Param("userId") Long userId,
                                          @Param("city") String city,
                                          @Param("category") String category);
}
