package com.skillswap.dto;

public record UserSkillDto(Long id, Long skillId, String skillName, String category,
                           String skillType, String experience, String proficiency) {}
