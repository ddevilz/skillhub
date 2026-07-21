package com.skillswap.service;

import com.skillswap.dto.*;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminReportService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final SkillRepository skillRepository;
    private final SessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumCategoryRepository forumCategoryRepository;

    public AdminReportService(UserRepository userRepository, UserSkillRepository userSkillRepository,
                              SkillRepository skillRepository, SessionRepository sessionRepository,
                              ReviewRepository reviewRepository, ForumPostRepository forumPostRepository,
                              ForumCategoryRepository forumCategoryRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.skillRepository = skillRepository;
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.forumPostRepository = forumPostRepository;
        this.forumCategoryRepository = forumCategoryRepository;
    }

    public List<DailyCountDto> usersOverTime() {
        Map<LocalDate, Long> byDay = userRepository.findAll().stream()
                .collect(Collectors.groupingBy(u -> u.getCreatedDate().toLocalDate(), Collectors.counting()));
        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyCountDto(e.getKey(), e.getValue()))
                .toList();
    }

    public List<SkillPopularityDto> popularSkills() {
        Map<Long, Long> countBySkill = userSkillRepository.findAll().stream()
                .collect(Collectors.groupingBy(UserSkill::getSkillId, Collectors.counting()));
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return countBySkill.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new SkillPopularityDto(e.getKey(),
                        skills.containsKey(e.getKey()) ? skills.get(e.getKey()).getSkillName() : null,
                        e.getValue()))
                .toList();
    }

    public SessionStatsDto sessionStats() {
        List<Session> all = sessionRepository.findAll();
        long pending = all.stream().filter(s -> s.getStatus() == SessionStatus.PENDING).count();
        long confirmed = all.stream().filter(s -> s.getStatus() == SessionStatus.CONFIRMED).count();
        long completed = all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        long cancelled = all.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).count();
        return new SessionStatsDto(pending, confirmed, completed, cancelled);
    }

    public List<TopMentorDto> topMentors() {
        Map<Long, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, List<Review>> byRated = reviewRepository.findAll().stream()
                .filter(r -> !r.isFlagged())
                .collect(Collectors.groupingBy(Review::getRatedUserId));
        return byRated.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToInt(Review::getRating).average().orElse(0.0);
                    User u = users.get(e.getKey());
                    return new TopMentorDto(e.getKey(), u != null ? u.getFullName() : null, avg, e.getValue().size());
                })
                .sorted(Comparator.comparingDouble(TopMentorDto::avgRating).reversed())
                .limit(10)
                .toList();
    }

    public List<CategoryActivityDto> activeCategories() {
        Map<Long, Long> countByCategory = forumPostRepository.findAll().stream()
                .filter(p -> !p.isModerated())
                .collect(Collectors.groupingBy(ForumPost::getCategoryId, Collectors.counting()));
        Map<Long, ForumCategory> categories = forumCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(ForumCategory::getId, c -> c));
        return countByCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(e -> new CategoryActivityDto(e.getKey(),
                        categories.containsKey(e.getKey()) ? categories.get(e.getKey()).getCategoryName() : null,
                        e.getValue()))
                .toList();
    }
}
