package com.skillswap.service;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.SkillDto;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.repository.UserSkillRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;

    public SkillService(SkillRepository skillRepository, UserSkillRepository userSkillRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
    }

    @Cacheable("skills")
    public List<SkillDto> catalog() {
        return skillRepository.findAll().stream()
                .map(s -> new SkillDto(s.getId(), s.getSkillName(), s.getCategory(), s.getDescription()))
                .toList();
    }

    @Cacheable("categories")
    public List<String> categories() {
        return skillRepository.findDistinctCategories();
    }

    public List<UserSkillDto> mySkills(Long userId) {
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return userSkillRepository.findByUserId(userId).stream()
                .map(us -> toDto(us, skills.get(us.getSkillId())))
                .toList();
    }

    @CacheEvict(value = "suggestions", allEntries = true)
    public UserSkillDto add(Long userId, AddUserSkillRequest req) {
        SkillType type = parseType(req.skillType());
        Skill skill = skillRepository.findById(req.skillId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        if (userSkillRepository.existsByUserIdAndSkillIdAndSkillType(userId, req.skillId(), type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill already added for this type");
        }
        UserSkill us = new UserSkill();
        us.setUserId(userId);
        us.setSkillId(req.skillId());
        us.setSkillType(type);
        us.setExperience(req.experience());
        us.setProficiency(req.proficiency());
        return toDto(userSkillRepository.save(us), skill);
    }

    @CacheEvict(value = "suggestions", allEntries = true)
    public void remove(Long userId, Long userSkillId) {
        UserSkill us = userSkillRepository.findByIdAndUserId(userSkillId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill entry not found"));
        userSkillRepository.delete(us);
    }

    private SkillType parseType(String raw) {
        try {
            return SkillType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "skillType must be CAN_TEACH or WANT_TO_LEARN");
        }
    }

    private UserSkillDto toDto(UserSkill us, Skill skill) {
        String name = skill != null ? skill.getSkillName() : null;
        String category = skill != null ? skill.getCategory() : null;
        return new UserSkillDto(us.getId(), us.getSkillId(), name, category,
                us.getSkillType().name(), us.getExperience(), us.getProficiency());
    }
}
