package com.skillswap.service;

import com.skillswap.dto.*;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminReportServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final ReviewRepository reviewRepo = mock(ReviewRepository.class);
    private final ForumPostRepository postRepo = mock(ForumPostRepository.class);
    private final ForumCategoryRepository categoryRepo = mock(ForumCategoryRepository.class);
    private final AdminReportService service = new AdminReportService(
            userRepo, userSkillRepo, skillRepo, sessionRepo, reviewRepo, postRepo, categoryRepo);

    private User user(Long id, String name) {
        User u = new User();
        u.setFullName(name);
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, id);
            var dateField = User.class.getDeclaredField("createdDate");
            dateField.setAccessible(true);
            dateField.set(u, LocalDateTime.of(2026, 7, 1, 12, 0));
        } catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    @Test
    void usersOverTimeGroupsByDay() {
        when(userRepo.findAll()).thenReturn(List.of(user(1L, "A"), user(2L, "B")));
        List<DailyCountDto> result = service.usersOverTime();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.get(0).count()).isEqualTo(2);
    }

    @Test
    void popularSkillsRanksByUsageDescending() {
        UserSkill us1 = new UserSkill(); us1.setSkillId(4L);
        UserSkill us2 = new UserSkill(); us2.setSkillId(4L);
        UserSkill us3 = new UserSkill(); us3.setSkillId(7L);
        when(userSkillRepo.findAll()).thenReturn(List.of(us1, us2, us3));
        Skill python = new Skill(); python.setSkillName("Python"); python.setCategory("Technology");
        when(skillRepo.findAll()).thenReturn(List.of(python));

        List<SkillPopularityDto> result = service.popularSkills();

        assertThat(result.get(0).skillId()).isEqualTo(4L);
        assertThat(result.get(0).count()).isEqualTo(2);
    }

    @Test
    void sessionStatsCountsEachStatus() {
        Session pending = new Session(); pending.setStatus(SessionStatus.PENDING);
        Session confirmed = new Session(); confirmed.setStatus(SessionStatus.CONFIRMED);
        Session completed1 = new Session(); completed1.setStatus(SessionStatus.COMPLETED);
        Session completed2 = new Session(); completed2.setStatus(SessionStatus.COMPLETED);
        when(sessionRepo.findAll()).thenReturn(List.of(pending, confirmed, completed1, completed2));

        SessionStatsDto dto = service.sessionStats();

        assertThat(dto.pending()).isEqualTo(1);
        assertThat(dto.confirmed()).isEqualTo(1);
        assertThat(dto.completed()).isEqualTo(2);
        assertThat(dto.cancelled()).isZero();
    }

    @Test
    void topMentorsExcludesFlaggedReviewsAndRanksByAverage() {
        Review good = new Review(); good.setRatedUserId(1L); good.setRating(5); good.setFlagged(false);
        Review flagged = new Review(); flagged.setRatedUserId(1L); flagged.setRating(1); flagged.setFlagged(true);
        when(reviewRepo.findAll()).thenReturn(List.of(good, flagged));
        when(userRepo.findAll()).thenReturn(List.of(user(1L, "Mentor")));

        List<TopMentorDto> result = service.topMentors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).avgRating()).isEqualTo(5.0);
        assertThat(result.get(0).reviewCount()).isEqualTo(1);
    }

    @Test
    void activeCategoriesExcludesModeratedPosts() {
        ForumPost visible = new ForumPost(); visible.setCategoryId(1L);
        ForumPost hidden = new ForumPost(); hidden.setCategoryId(1L); hidden.setModerated(true);
        when(postRepo.findAll()).thenReturn(List.of(visible, hidden));
        ForumCategory cat = new ForumCategory(); cat.setCategoryName("Music");
        when(categoryRepo.findAll()).thenReturn(List.of(cat));

        List<CategoryActivityDto> result = service.activeCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).postCount()).isEqualTo(1);
    }
}
