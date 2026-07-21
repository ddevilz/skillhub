package com.skillswap.service;

import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SessionStatus;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillBadgeRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.repository.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BadgeService {

    /** Completed-teaching-session-count thresholds per skill. Upgrade path: make configurable if the rule ever needs tuning without a redeploy. */
    public static final int BEGINNER_THRESHOLD = 1;
    public static final int INTERMEDIATE_THRESHOLD = 5;
    public static final int EXPERT_THRESHOLD = 15;

    private final SessionRepository sessionRepository;
    private final SkillBadgeRepository skillBadgeRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;

    public BadgeService(SessionRepository sessionRepository, SkillBadgeRepository skillBadgeRepository,
                         UserRepository userRepository, SkillRepository skillRepository) {
        this.sessionRepository = sessionRepository;
        this.skillBadgeRepository = skillBadgeRepository;
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
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

    /** Admin-only grant — VERIFIED is never awarded by evaluateAndAward's rule engine. Idempotent. */
    public void awardVerified(Long userId, Long skillId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
        }
        if (skillBadgeRepository.existsByUserIdAndSkillIdAndBadgeType(userId, skillId, BadgeType.VERIFIED)) {
            return;
        }
        SkillBadge b = new SkillBadge();
        b.setUserId(userId);
        b.setSkillId(skillId);
        b.setBadgeType(BadgeType.VERIFIED);
        skillBadgeRepository.save(b);
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
