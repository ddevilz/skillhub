package com.skillswap.service;

import com.skillswap.dto.BadgeDto;
import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SessionStatus;
import com.skillswap.entity.SkillBadge;
import com.skillswap.entity.User;
import com.skillswap.entity.Skill;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillBadgeRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.repository.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BadgeServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final SkillBadgeRepository badgeRepo = mock(SkillBadgeRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final BadgeService service = new BadgeService(sessionRepo, badgeRepo, userRepo, skillRepo);

    @Test
    void doesNothingBelowBeginnerThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(0L);
        service.evaluateAndAward(1L, 4L);
        verify(badgeRepo, never()).save(any());
    }

    @Test
    void grantsBeginnerAtThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(1L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.BEGINNER)).thenReturn(false);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, times(1)).save(any(SkillBadge.class));
    }

    @Test
    void grantsBeginnerAndIntermediateAtIntermediateThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(5L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(eq(1L), eq(4L), any())).thenReturn(false);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, times(2)).save(any(SkillBadge.class));
    }

    @Test
    void skipsAlreadyAwardedBadge() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(1L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.BEGINNER)).thenReturn(true);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, never()).save(any());
    }

    @Test
    void badgesForReturnsUserBadges() {
        SkillBadge b = new SkillBadge();
        when(badgeRepo.findByUserId(1L)).thenReturn(List.of(b));
        assertThat(service.badgesFor(1L)).hasSize(1);
    }

    @Test
    void awardVerifiedGrantsBadge() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(new User()));
        when(skillRepo.findById(4L)).thenReturn(Optional.of(new Skill()));
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.VERIFIED)).thenReturn(false);
        service.awardVerified(1L, 4L);
        verify(badgeRepo).save(any(SkillBadge.class));
    }

    @Test
    void awardVerifiedIsIdempotent() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(new User()));
        when(skillRepo.findById(4L)).thenReturn(Optional.of(new Skill()));
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.VERIFIED)).thenReturn(true);
        service.awardVerified(1L, 4L);
        verify(badgeRepo, never()).save(any());
    }
}
