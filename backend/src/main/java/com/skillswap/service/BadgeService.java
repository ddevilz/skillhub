package com.skillswap.service;

import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SessionStatus;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillBadgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BadgeService {

    /** Completed-teaching-session-count thresholds per skill. Upgrade path: make configurable if the rule ever needs tuning without a redeploy. */
    public static final int BEGINNER_THRESHOLD = 1;
    public static final int INTERMEDIATE_THRESHOLD = 5;
    public static final int EXPERT_THRESHOLD = 15;

    private final SessionRepository sessionRepository;
    private final SkillBadgeRepository skillBadgeRepository;

    public BadgeService(SessionRepository sessionRepository, SkillBadgeRepository skillBadgeRepository) {
        this.sessionRepository = sessionRepository;
        this.skillBadgeRepository = skillBadgeRepository;
    }

    /** Cumulative: awards every threshold newly reached, keeps all earned tiers, safe to call repeatedly. */
    public void evaluateAndAward(Long teacherUserId, Long skillId) {
        long count = sessionRepository.countByTeacherUserIdAndSkillIdAndStatus(
                teacherUserId, skillId, SessionStatus.COMPLETED);
        awardIfReached(teacherUserId, skillId, count, BEGINNER_THRESHOLD, BadgeType.BEGINNER);
        awardIfReached(teacherUserId, skillId, count, INTERMEDIATE_THRESHOLD, BadgeType.INTERMEDIATE);
        awardIfReached(teacherUserId, skillId, count, EXPERT_THRESHOLD, BadgeType.EXPERT);
    }

    public List<SkillBadge> badgesFor(Long userId) {
        return skillBadgeRepository.findByUserId(userId);
    }

    private void awardIfReached(Long userId, Long skillId, long count, int threshold, BadgeType type) {
        if (count < threshold) return;
        if (skillBadgeRepository.existsByUserIdAndSkillIdAndBadgeType(userId, skillId, type)) return;
        SkillBadge b = new SkillBadge();
        b.setUserId(userId);
        b.setSkillId(skillId);
        b.setBadgeType(type);
        skillBadgeRepository.save(b);
    }
}
