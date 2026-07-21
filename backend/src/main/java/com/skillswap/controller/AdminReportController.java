package com.skillswap.controller;

import com.skillswap.dto.*;
import com.skillswap.service.AdminReportService;
import com.skillswap.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final CurrentUser currentUser;

    public AdminReportController(AdminReportService adminReportService, CurrentUser currentUser) {
        this.adminReportService = adminReportService;
        this.currentUser = currentUser;
    }

    @GetMapping("/users-over-time")
    public List<DailyCountDto> usersOverTime() {
        currentUser.requireAdmin();
        return adminReportService.usersOverTime();
    }

    @GetMapping("/popular-skills")
    public List<SkillPopularityDto> popularSkills() {
        currentUser.requireAdmin();
        return adminReportService.popularSkills();
    }

    @GetMapping("/session-stats")
    public SessionStatsDto sessionStats() {
        currentUser.requireAdmin();
        return adminReportService.sessionStats();
    }

    @GetMapping("/top-mentors")
    public List<TopMentorDto> topMentors() {
        currentUser.requireAdmin();
        return adminReportService.topMentors();
    }

    @GetMapping("/active-categories")
    public List<CategoryActivityDto> activeCategories() {
        currentUser.requireAdmin();
        return adminReportService.activeCategories();
    }
}
