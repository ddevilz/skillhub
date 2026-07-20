package com.skillswap.controller;

import com.skillswap.dto.BadgeDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SkillRepository;
import com.skillswap.service.BadgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/{id}/badges")
public class BadgeController {

    private final BadgeService badgeService;
    private final SkillRepository skillRepository;

    public BadgeController(BadgeService badgeService, SkillRepository skillRepository) {
        this.badgeService = badgeService;
        this.skillRepository = skillRepository;
    }

    @GetMapping
    public List<BadgeDto> badges(@PathVariable Long id) {
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return badgeService.badgesFor(id).stream()
                .map(b -> new BadgeDto(b.getId(), b.getSkillId(),
                        skills.containsKey(b.getSkillId()) ? skills.get(b.getSkillId()).getSkillName() : null,
                        b.getBadgeType().name(), b.getAwardedDate()))
                .toList();
    }
}
