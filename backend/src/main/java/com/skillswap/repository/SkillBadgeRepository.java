package com.skillswap.repository;

import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SkillBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillBadgeRepository extends JpaRepository<SkillBadge, Long> {
    List<SkillBadge> findByUserId(Long userId);
    boolean existsByUserIdAndSkillIdAndBadgeType(Long userId, Long skillId, BadgeType badgeType);
}
